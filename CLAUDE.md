# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AI Test Case Generator — generates executable test cases from plain-English user stories using the Claude API (Anthropic Java SDK), then executes them via Playwright (UI) and RestAssured (API), with Allure reporting, self-healing selectors, AI output quality scoring, and full CI/CD pipelines.

## Build & Test Commands

```bash
# One-time: install Playwright browsers
mvn exec:java -Dexec.mainClass="com.microsoft.playwright.CLI" -Dexec.args="install chromium"

# Run smoke tests only (34/58 tests tagged "smoke") — fast gate ~5 min
mvn test -Dsmoke.only=true -Dtestng.suite.file=src/test/resources/testng-smoke.xml

# Run all 58 tests (9 suites: 4 UI + 1 E2E + 4 API)
mvn test

# Run framework unit tests only
mvn test -Dtest=TestCaseValidatorTest,SensitiveDataMaskerTest

# Generate Allure HTML report after tests
mvn allure:report

# Open live Allure report in browser
mvn allure:serve
```

**API MODE only:** Set `ANTHROPIC_API_KEY` env var when STORY_FILE points to a `.txt` story.
**FILE MODE** (default): no API key needed — STORY_FILE points to a pre-generated `.json`.

## Architecture

**Pipeline:**
```
User Story (.txt) → TestCaseGenerator → ClaudeService (Anthropic SDK)
    → raw JSON → TestCaseValidator (schema + allow-list)
    → invalid? retry once with error context → TestSuite
    → TestQualityScorer → AgentActivity (score recorded)
    → BaseTest (@DataProvider) → PlaywrightExecutor (UI) / RestAssuredExecutor (API)
    → QualityGateChecker (@AfterTest) → RunHistoryStore → TrendDashboard
    → AllureReporter
```

**Key classes and their roles:**

| Class | Role |
|---|---|
| `ClaudeService` | Anthropic SDK wrapper — `generateTestCases()` + `retryWithError()`. Strips markdown fences. |
| `PromptTemplates` | All system/user/retry prompts. Edit here for prompt tuning. Enforces `${VAR}` for sensitive data; hardcoded secrets are forbidden in generated JSON. |
| `TestCaseValidator` | Multi-layer validation: syntax → required fields → type-specific → allow-lists. |
| `TestCaseGenerator` | Orchestrates: read story → generate → validate → retry once → score → enrich `TestSuite`. |
| `TestQualityScorer` | Scores AI-generated test suites on 4 dimensions (assertion depth, negative coverage, edge case coverage, step realism). Returns 0–100 score with tier label (EXCELLENT/GOOD/FAIR/POOR/CRITICAL). |
| `QualityGateChecker` | Runs in `@AfterTest` — enforces pass rate ≥80% and flake rate ≤20% per suite. Writes `target/quality-gate-failure.txt` and throws `QualityGateException` on violation. |
| `SelfHealSuggester` | Triggered when all fallback selectors are exhausted. Sends live DOM snapshot to Claude and returns selector repair suggestions as Allure attachment + `target/repair-suggestions/{testId}.json`. Human-in-the-loop — suggestions are not auto-applied. |
| `PlaywrightExecutor` | Executes UI steps at viewport 1280×800. Self-healing (step fallbacks + PageRegistry). `assert_accessible` runs an in-browser JS audit (title, landmarks, alt text, form labels). On failure: viewport screenshot, DOM snapshot, browser console log, cookies (values masked), HAR (secrets scrubbed), diagnostics report. Per-step screenshots always attached. |
| `RestAssuredExecutor` | Executes API test cases via RestAssured. `schema_file` assertions validate response body against JSON Schema; `response_time_ms` assertions enforce SLA thresholds. Attaches request (headers masked), response, response time, and cURL command on every call. |
| `SensitiveDataMasker` | Central masking utility. Detects sensitive keys (`password`, `secret`, `token`, `auth`, `api_key`, etc.). Provides `maskIfSensitive()`, `scrubJson()`, `scrubFormEncoded()`. Used by `TestDataResolver`, `RestAssuredExecutor`, and `PlaywrightExecutor`. |
| `AllureReporter` | Writes `categories.json`, `environment.properties`, `executor.json`; preserves Allure history; saves agent reports to `target/agent-reports/` and `test-history/agent-reports/`. |
| `AgentActivity` | Thread-safe singleton collecting locator resolutions, self-heals, variable substitutions (sensitive values masked), API calls, and quality score during a run. Reset per `@BeforeTest`. |
| `RunContext` | JVM-scoped UUID + timestamp stamped on all artifacts for a run. |
| `RunHistoryStore` | Appends run summaries (including quality score) to `test-history/runs.json` (outside `target/`, survives `mvn clean`). Keeps last 50 entries (`MAX_RUNS`). |
| `TrendDashboard` | Generates `test-history/trend-dashboard.html` — 5 Chart.js charts (pass/fail, self-heals, coverage, avg response time, AI quality score) + run history table. |
| `ConfigManager` | Singleton reading `config.properties`. Resolution order: system property (`-D`) → env var → `config.properties` → default. |
| `TestDataResolver` | Resolves `${VAR}` placeholders. Checks `System.getenv(key)` first, then `test-data.properties`. Sensitive key values logged/recorded as `[MASKED]` — never in reports. |
| `PageRegistry` | Central fallback-selector lookup built from all Page Object classes. |
| `BaseTest` | `@BeforeTest`/`@AfterTest` lifecycle (one cycle per `<test>` block in testng.xml); shared `@DataProvider` (smoke filter: filters to `"smoke"`-tagged tests when `-Dsmoke.only=true`) + `@Test`; per-module quality scoring, gate check, agent reporting, and trend update. |
| `FlakeRetryAnalyzer` | `IRetryAnalyzer` — retries only on Playwright `TimeoutError`, not assertion failures. |
| `RetryAnnotationTransformer` | `IAnnotationTransformer` — wires `FlakeRetryAnalyzer` globally to every `@Test`. Registered in `testng.xml`. |
| `AITestRunner_*` | 3-line runner subclasses — declare `STORY_FILE`, `BASE_URL`, `getModuleName()`. |

**Model classes:** `TestCase` is polymorphic — UI tests have `steps[]`, API tests have `request{}` + `assertions[]`.
`TestStep` has an optional `fallback_targets` list — selectors tried in order when the primary target times out.

## Hallucination Mitigation Strategy

Three enforcement layers:
1. **System prompt** — JSON-only output, explicit action/assertion allow-lists, selector guidance, `${VAR}` mandate for secrets
2. **Validator** — Rejects non-conforming JSON before any execution
3. **Retry** — On validation failure, feeds the error back to Claude for one retry; security reminders repeated

**Allow-lists (hardcoded in `TestCaseValidator`):**
- UI actions: `navigate, fill, click, assert_visible, assert_text, assert_url_contains, wait_for, select, assert_accessible`
- API assertion types: `status_code, json_path, header, body_contains, not_empty, schema_file, response_time_ms`

To add a new action/assertion type: update `ALLOWED_ACTIONS`/`ALLOWED_ASSERTION_TYPES` in `TestCaseValidator`, add handler logic in the relevant executor, and update the system prompt in `PromptTemplates`.

## Sensitive Data Masking

`SensitiveDataMasker` is the single enforcement point for keeping secrets out of reports.

**Key detection** — any key whose lower-cased name contains: `pass`, `password`, `passwd`, `secret`, `token`, `apikey`, `api_key`, `api-key`, `authorization`, `auth`.

**Where masking is applied:**

| Location | What is masked |
|---|---|
| `TestDataResolver` | Resolved value for sensitive keys logged/recorded as `[MASKED]` in `AgentActivity` |
| `RestAssuredExecutor` — request attachment | Sensitive header values → `[MASKED]`; request body scrubbed via `scrubJson` + `scrubFormEncoded` |
| `RestAssuredExecutor` — cURL command | Same header masking applied to the cURL reproduction string |
| `PlaywrightExecutor` — cookies | All cookie values → `[MASKED]`; names and domains shown |
| `PlaywrightExecutor` — HAR | Full HAR JSON scrubbed via `scrubJson` + `scrubFormEncoded` before attaching |

**Env var override** — `TestDataResolver` checks `System.getenv(key)` before `test-data.properties`. In CI/CD, inject real credentials as pipeline secrets; the `.properties` file holds only public demo values.

**Never store in source**: `test-data-secrets.properties`, `*-credentials.properties`, `secrets.properties` are gitignored.

## Quality Gate

`QualityGateChecker` enforces two thresholds per suite in `@AfterTest`:

| Gate | Default threshold | Config key |
|---|---|---|
| Minimum pass rate | 80% | `quality.gate.min.pass.pct` |
| Maximum flake rate | 20% | `quality.gate.max.flake.pct` |

On violation: writes `target/quality-gate-failure.txt` with details, then throws `QualityGateException`. The GitHub Actions CI step explicitly checks for this file and fails the job — belt and suspenders enforcement.

Thresholds are configurable in `src/main/resources/config.properties`.

## Failure Artifacts Captured

### Every test failure
| Artifact | Source |
|---|---|
| Test name, ID, duration, timestamp | Allure automatic |
| Full stack trace | Allure automatic (exception propagated) |
| Run ID | `RunContext` → `executor.json` + `environment.properties` |
| Retry attempt | `FlakeRetryAnalyzer` → Allure retried result entries |
| Step-by-step breakdown | Allure steps + per-step screenshots |

### UI test failures (`PlaywrightExecutor`)
| Artifact | Detail |
|---|---|
| Viewport screenshot (1280×800) | Captured at point of failure; viewport-only for reliability in containerized environments |
| Per-step screenshots | Attached after every step (pass or fail) |
| DOM snapshot (HTML) | `page.content()` at failure |
| Browser console log | All `console.*` messages collected during the test |
| Viewport size | `1280×800` (fixed); attached as text and in diagnostics |
| Cookies / session state | Names + domains shown; values always `[MASKED]` |
| HAR network traffic | Full HAR scrubbed of sensitive values |
| Failure diagnostics | Test ID, URL, browser, headless, timeout, viewport, root cause + stack |
| Self-heal events | Per-heal attachment: primary selector and healed-with selector |
| AI repair suggestions | When all fallbacks exhausted: Claude's suggested selectors as Allure attachment + `.json` artifact |
| Accessibility violations | `assert_accessible` attaches audit report listing specific WCAG violations |

### API test artifacts (`RestAssuredExecutor`) — attached on every request
| Artifact | Detail |
|---|---|
| Request (method, URL, params, headers, body) | Headers and body scrubbed of sensitive values |
| Response (status, headers, body) | Full pretty-printed body |
| Response time | Milliseconds — compared against `response_time_ms` threshold when assertion present |
| cURL reproduction command | Copy-paste ready; sensitive headers masked |

## Key Configuration Files

| File | Purpose |
|---|---|
| `src/main/resources/config.properties` | Browser type, headless flag, timeout, retry max, quality gate thresholds |
| `src/main/resources/test-data.properties` | `${VAR}` values substituted at runtime. Env vars override at runtime — store real secrets in env, not here. |
| `src/main/resources/schemas/` | JSON Schema files (`user.schema.json`, `post.schema.json`) used by `schema_file` assertions |
| `src/test/resources/testng.xml` | Full suite — all 9 runners, parallel (thread-count=3) |
| `src/test/resources/testng-smoke.xml` | Smoke gate — all 9 runners, parallel. 34/58 tests tagged "smoke" run; DataProvider filters the rest. |
| `src/test/resources/testng-docker.xml` | Docker full suite — `parallel="none"`, serial execution for containerized Chromium stability |
| `src/test/resources/testng-smoke-docker.xml` | Docker smoke gate — serial execution + `-Dsmoke.only=true`. Selected by `entrypoint.sh` when `SMOKE_ONLY=true`. |
| `src/test/resources/testng-ui-docker.xml` | Docker UI-only suite — 5 Playwright runners, serial. Selected when `UI_ONLY=true`. |
| `src/test/resources/testng-api-docker.xml` | Docker API-only suite — 4 RestAssured runners, parallel. Selected when `API_ONLY=true`. |

## Page Object Model

`src/main/java/com/qa/ai/pages/` holds selector registries (not Playwright Page objects):
- `LoginPage`, `InventoryPage`, `ProductDetailPage`, `BurgerMenuComponent` — primary + fallback selectors
- `PageRegistry` — merged lookup used by `PlaywrightExecutor` for self-healing at runtime

To add a new page: extend `BasePage`, implement `selectors()` and optionally `fallbacks()`, register in `PageRegistry.PAGES`.

## Adding a New Test Runner

Create a subclass of `BaseTest` with three methods:
```java
public class AITestRunner_CheckoutTests extends BaseTest {
    private static final String STORY_FILE = "src/main/resources/stories/generated/checkout-ui-tests.json";
    private static final String BASE_URL   = "https://www.saucedemo.com";
    @Override protected String getStoryFilePath() { return STORY_FILE; }
    @Override protected String getBaseUrl()       { return BASE_URL; }
    @Override protected String getModuleName()    { return "Checkout"; }
}
```
Then add a `<test>` block to `testng.xml`. No other code needed — `@DataProvider`, `@Test`, `@BeforeTest`, `@AfterTest`, quality scoring, and quality gate are all inherited.

**Active runners:**
- UI: `AITestRunner_LoginPageTests`, `AITestRunner_ProductsPageTests`, `AITestRunner_ProductDetailTests`, `AITestRunner_AddToCartTests`, `AITestRunner_CheckoutE2ETests`
- API: `AITestRunner_UserApiTests`, `AITestRunner_PostsApiTests`, `AITestRunner_CommentsApiTests`, `AITestRunner_TodosApiTests`

## Framework Unit Tests

`src/test/java/com/qa/ai/unit/` — validates the framework's own critical logic:

| Test class | What it covers |
|---|---|
| `TestCaseValidatorTest` | Valid UI/API JSON passes; disallowed actions rejected; missing required fields rejected; XSS/injection payloads in values rejected |
| `SensitiveDataMaskerTest` | Sensitive key detection; `maskIfSensitive()`; `scrubJson()` for nested objects; `scrubFormEncoded()` |

Run independently: `mvn test -Dtest=TestCaseValidatorTest,SensitiveDataMaskerTest`

## Key Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| Anthropic Java SDK | 2.30.0 | Claude API integration |
| Playwright Java | 1.44.0 | Browser automation |
| RestAssured | 5.4.0 | API testing DSL |
| REST Assured JSON Schema Validator | 5.4.0 | JSON Schema contract validation |
| TestNG | 7.10.2 | Test orchestration |
| Allure TestNG | 2.27.0 | HTML reporting |
| Jackson | 2.17.1 | JSON parsing |

Claude model: `claude-sonnet-4-6` (configured in `ClaudeService`).

## Output Folders

| Path | Contents | Survives `mvn clean`? |
|---|---|---|
| `target/allure-results/` | Raw Allure results + agent-activity txt/json per run | No |
| `target/agent-reports/` | `{runId}-{Module}.html` + `.json` — easy to browse per run | No |
| `target/repair-suggestions/` | `{testId}.json` — Claude's selector repair suggestions when triggered | No |
| `target/site/allure-maven-plugin/` | Generated Allure HTML report | No |
| `test-history/runs.json` | Cumulative run summaries incl. quality score (last 50), drives trend charts | Yes |
| `test-history/agent-reports/` | Persistent HTML agent reports linked from trend dashboard | Yes |
| `test-history/trend-dashboard.html` | 5-chart Chart.js dashboard — open in browser, no server needed | Yes |

## Logs

Log4j2 writes to console and `target/logs/ai-test-generator.log` (rolling, 10 MB/file, 7 files). Package `com.qa.ai.*` at DEBUG; Playwright/RestAssured/Anthropic SDK at INFO.

## CI/CD

### Docker

| File | Purpose |
|---|---|
| `Dockerfile` | Single image — Java 17 + Maven + Playwright browsers (chromium/firefox/webkit). Two-layer cache: Maven deps layer invalidated only on `pom.xml` change; browser layer invalidated only on Playwright version change. |
| `docker/entrypoint.sh` | Bridges Docker env vars (`BROWSER`, `HEADLESS`, `SMOKE_ONLY`) to Maven `-D` system properties. Includes DNS readiness probe (waits up to 30 s for both test targets). Selects appropriate `testng-*-docker.xml` based on env flags. |
| `.dockerignore` | Excludes `target/`, `.git/`, secrets files from build context. |
| `docker-compose.yml` | Five services: `smoke` (Chromium gate, 34 smoke tests) → `chromium-ui`, `firefox-ui`, `webkit-ui` (UI only, parallel after gate) + `api` (29 API tests, once). `depends_on: condition: service_completed_successfully` enforces the smoke gate. Chromium containers configured with `shm_size: 512mb`. |
| `ci/merge-and-report.sh` | Post-compose script: extracts results from named Docker volumes, merges, generates Allure report. |

**Single browser:**
```bash
docker build -t ai-test-generator .
docker run --rm -e BROWSER=firefox -e LOGIN_PASS=secret_sauce \
  -v "$(pwd)/target:/app/target" ai-test-generator
```

**All browsers (parallel):**
```bash
docker-compose up --build      # waits for all services to finish
./ci/merge-and-report.sh       # merges + generates HTML report
```

**Secrets** — create `.env` in project root (gitignored):
```
ANTHROPIC_API_KEY=sk-ant-...
LOGIN_PASS=your_password
```

### Jenkins

`Jenkinsfile` — declarative pipeline with:
- `Build Docker Image` → builds tagged image
- `Test — All Browsers` → parallel Chromium / Firefox / WebKit stages, each a separate Docker container
- `Merge Results` → combines the 3 result dirs
- `Generate Allure Report` → runs `mvn allure:report`
- `post.always` → publishes Allure (Allure Plugin), archives raw results + agent reports + logs, cleans image
- `post.failure/fixed` → email notifications via Email Extension plugin

**Jenkins credentials required** (Manage Credentials → Global):
| Credential ID | Kind | Used for |
|---|---|---|
| `anthropic-api-key` | Secret text | API mode only (FILE mode needs none) |
| `saucedemo-login-pass` | Secret text | `LOGIN_PASS` env var injected into containers |

### GitHub Actions

`.github/workflows/ci.yml` — smoke-first pipeline:

**Job `test` (matrix: chromium/firefox/webkit)**
- Runs on `push`/`PR` to `main`/`develop`, nightly cron (`0 6 * * 1-5`), and manual dispatch
- `cancel-in-progress: true` — superseded runs on the same branch are cancelled
- `fail-fast: false` — one browser failing does not cancel the others
- Installs Playwright browsers with `--with-deps` (includes system-level dependencies)
- Passes secrets via `LOGIN_PASS: ${{ secrets.LOGIN_PASS }}`
- Uploads `allure-results-{browser}` + `agent-reports-{browser}` as artifacts (7 days)
- Uploads logs only on failure (3 days)
- Fails explicitly if `target/quality-gate-failure.txt` exists

**Job `report`** (runs after `test`, even on failure)
- Downloads all three `allure-results-*` artifacts
- Merges into `target/allure-results-merged`
- Generates Allure HTML report
- Uploads `allure-report` artifact (30 days)
- Deploys to GitHub Pages on `main` push (requires Pages enabled in repo settings)

**Secrets required** (Settings → Secrets → Actions):
| Secret | Purpose |
|---|---|
| `LOGIN_PASS` | Test account password |
| `ANTHROPIC_API_KEY` | API mode only — not needed for FILE mode |
