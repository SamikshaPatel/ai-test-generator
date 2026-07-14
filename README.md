# AI Test Case Generator — QA Portfolio Project

> **Plain-English user story → executable, self-healing, AI-evaluated test suite — powered by Claude AI**

[![CI](https://github.com/SamikshaPatel/ai-test-generator/actions/workflows/ci.yml/badge.svg)](https://github.com/SamikshaPatel/ai-test-generator/actions/workflows/ci.yml)
[![Tests](https://img.shields.io/badge/tests-58%20passing-brightgreen)](#test-suites)
[![Java](https://img.shields.io/badge/Java-17-blue?logo=openjdk)](https://openjdk.org/projects/jdk/17/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Made with Claude](https://img.shields.io/badge/AI-Claude%20Sonnet%204.6-blueviolet)](https://www.anthropic.com)

A Java-based AI-augmented test automation framework that reads a plain-English user story, calls the Claude API to generate structured test cases, validates them against a 3-layer hallucination guardrail, then executes them across Playwright (UI) and RestAssured (API) — with self-healing selectors, JSON Schema contract validation, response time SLA assertions, accessibility auditing, AI output quality scoring, agentic repair suggestions, a trend dashboard, and full CI/CD pipelines (GitHub Actions + Jenkins + Docker).

**No API key needed to run** — pre-generated JSON stories are included so you can execute all 58 tests immediately after cloning.

---

## QA Maturity Journey

This project documents a complete shift from manual, reactive QA to AI-assisted, measurable QA engineering.

| Dimension | Before | After (this framework) |
|---|---|---|
| **Test authoring** | Engineers hand-write test cases per sprint | Claude generates structured test cases from plain-English stories — non-engineers can contribute |
| **Selector maintenance** | Manual investigation when UI changes break tests | 2-level self-healing (step fallbacks + PageRegistry) recovers at runtime, zero manual fix needed |
| **CI feedback** | Full suite on every push (~15 min wait) | Smoke gate (32/58 tests, ~5 min) on every push; full suite only when needed |
| **API confidence** | Status code checks only | JSON Schema contract validation + SLA assertions on every API suite |
| **Accessibility** | Separate audit tool, run quarterly | Inline `assert_accessible` step in every UI suite, fails the test on violation |
| **AI output quality** | Generated tests accepted uncritically | `AITestOutput_QualityScorer` evaluates every generation on 4 dimensions (0–100), trend-tracked over time |
| **Test effectiveness** | No signal on whether tests actually worked at runtime | `TestEffectivenessScorer` scores pass rate, selector stability, flake resistance, and selector coverage after every suite run |
| **Selector failure recovery** | Engineer debugs DOM manually | `SelfHealSuggester` sends live DOM snapshot to Claude, returns repair suggestions as Allure attachment |
| **Release governance** | Tests pass/fail but no gate enforced | `QualityGateChecker` blocks release if pass rate drops below 80% or flake rate exceeds 20% |
| **QA process visibility** | Pass/fail count in CI | Trend dashboard: 4 charts (pass/fail, self-heals, coverage, AI quality score) across 50 runs |
| **Secret handling** | Depends on developer discipline | Enforced centrally — `SensitiveDataMasker` scrubs secrets from all reports, HAR files, cURL commands |

---

## QA Leadership Highlights

Each feature maps to an engineering or business decision made at the QA Manager / Lead level — not just implementation.

| Decision | What it shows |
|---|---|
| **AI generates test cases from prose** | Reduces test-authoring time, enables non-engineers to contribute stories, scales coverage without headcount |
| **3-layer hallucination guardrails** | Production-grade safety thinking: never trust AI output without validation; retry with error context before rejecting |
| **Self-healing selectors (2 levels)** | Reduces flake-driven maintenance cost — teams waste days chasing selector breakage; this buys back that time |
| **Smoke-first CI gate (34/58)** | Fail fast on every PR, full suite only when needed — balances feedback speed vs. infrastructure cost |
| **Sensitive data masking** | Security by design: secrets never reach test reports, Allure, CI logs, or HAR files — compliance-ready |
| **Flake retry on TimeoutError only** | Distinguishes infrastructure flake from real failures — doesn't mask regressions, unlike naive retry-all |
| **AI Quality Scorer** | Measures AI output on 4 dimensions; enables A/B comparison of prompt versions, tracks generation quality over time |
| **Agentic repair loop** | Human-in-the-loop AI repair: Claude suggests selectors from live DOM, engineer reviews before applying — no blind auto-patch |
| **Quality gate in CI** | Release governance owned by QA: CI fails explicitly if pass rate drops or flake rate spikes — not just "tests ran" |
| **JSON Schema contract validation** | API contract confidence without a Pact server — validates response shape, types, and required fields on every run |
| **Agent Activity + Trend Dashboard** | Observability for the QA process itself: coverage trends, self-heal rate, AI quality scores across every run |
| **Docker serial execution for Rosetta** | Root-cause analysis over workarounds — diagnosed Chromium x86 emulation instability, fixed at the right layer |
| **Jenkins + GitHub Actions + Docker** | Framework runs in any CI environment — no single-vendor lock-in, enterprise-ready |
| **3-line runner extension pattern** | Designed for team adoption: any team member can add a new suite without touching shared infrastructure |

---

## Business Impact

| Metric | Without This Framework | With This Framework |
|---|---|---|
| Test case authoring | Manual writing by engineers (~30 min per suite) | AI-generated from plain-English stories (~2 min) |
| New suite onboarding | Hours (framework setup, scaffolding) | Minutes (3-line runner subclass) |
| Selector breakage recovery | Manual investigation + fix | Automatic self-healing, recorded in report |
| CI feedback on PR | Full suite wait (~15 min) | Smoke gate first (~5 min), full suite on merge |
| API contract regressions | Caught in staging by manual check | Caught on every CI run via JSON Schema assertion |
| Secret leakage in reports | Depends on developer discipline | Enforced centrally — no secrets ever reach reports |
| AI generation quality | Unknown — no measurement | Scored 0–100 per run, trended across 50 runs |
| Failing release detection | Test count only | Pass rate + flake rate gates enforced before deploy |
| Test run visibility | Pass/fail count only | Per-run agent activity, 4-chart trend dashboard, self-heal count |

---

## What This Demonstrates

| Capability | Implementation |
|---|---|
| **AI-driven test generation** | Claude `claude-sonnet-4-6` generates structured JSON test cases from prose user stories |
| **Hallucination guardrails** | 3-layer defence: system prompt allow-lists → JSON schema validator → retry with error context |
| **Self-healing selectors** | `PlaywrightExecutor` falls back through step-level + PageRegistry fallback chains on `TimeoutError` |
| **Agentic test repair** | `SelfHealSuggester` sends live DOM snapshot to Claude when all fallbacks fail — returns repair suggestions as Allure attachment + `.json` artifact |
| **AI output quality scoring** | `AITestOutput_QualityScorer` scores every AI generation on assertion depth, negative coverage, edge case coverage, and step realism (0–100, tier-labelled) |
| **Quality gate enforcement** | `QualityGateChecker` in `@AfterTest` enforces pass rate ≥80% and flake rate ≤20% — writes `quality-gate-failure.txt` and throws on violation |
| **JSON Schema contract validation** | `schema_file` assertion type validates API response bodies against `schemas/user.schema.json` / `post.schema.json` — catches shape regressions |
| **Response time SLA assertions** | `response_time_ms` assertion type asserts response is under threshold (e.g. 3000ms) — fails the test if SLA is breached |
| **Accessibility testing** | `assert_accessible` action runs in-browser JS audit (title, landmarks, alt text, form labels) — attaches violation list to Allure on failure |
| **Full E2E journey coverage** | 5-test Checkout E2E suite: login → browse → add to cart → checkout → confirmation, all smoke-tagged |
| **Page Object Model** | Logical names in JSON (`login_button`) resolved at runtime via `PageRegistry` — JSON never contains raw CSS |
| **Test data injection** | `${VAR}` placeholders in steps resolved from `test-data.properties` at runtime |
| **Smoke-first CI gate** | 32/58 tests tagged `smoke` run on every push/PR (~5 min); full suite only after gate passes |
| **Agent Activity reporting** | Per-run HTML report: locator resolutions, self-heal events, unknown targets, API calls, quality score, coverage % |
| **Trend dashboard** | Chart.js HTML dashboard — 4 charts: pass/fail, self-heals, page coverage, AI quality score |
| **Flake retry** | `IAnnotationTransformer` wires `FlakeRetryAnalyzer` globally — retries `TimeoutError` only, never assertion failures |
| **Docker multi-browser** | Single image, three parallel containers (chromium/firefox/webkit), results merged into one Allure report |
| **CI/CD** | GitHub Actions (smoke gate + full suite + Pages deploy) and Jenkins (smoke + full + Allure publish) |
| **Security** | `SensitiveDataMasker` scrubs secrets from all headers, HAR files, cURL commands, cookies, and Allure reports |
| **Framework self-tests** | `TestCaseValidatorTest` (14 tests) + `SensitiveDataMaskerTest` (17 tests) — the framework validates itself |

---

## Screenshots

### Allure Report — 58/58 Passing, 100%
![Allure Overview](docs/screenshots/allure-overview.png)

### Allure Suites — Full Test Detail with Step Breakdown
![Allure Suites](docs/screenshots/allure-suites.png)

### Self-Healing in Action — Terminal Output
Intentionally broken selectors auto-recovered at runtime. No test failures, no manual fix needed.
![Self-Heal Banner](docs/screenshots/self-heal-banner.png)

### Trend Dashboard — 4 Charts, 50 Runs Tracked
Built-in Chart.js dashboard tracking pass/fail trends, self-heal frequency, page registry coverage, and AI quality score across every run.
![Trend Dashboard](docs/screenshots/trend-dashboard.png)

### Agent Activity Report — Locator Resolutions + Masked Secrets
Per-run HTML report showing exactly which selectors resolved, which healed, which variables were substituted (sensitive values always masked), and the AI quality score for this run.
![Agent Activity Report](docs/screenshots/agent-activity.png)

---

## Quick Start (no API key required)

```bash
# 1. Install Playwright browser once
mvn exec:java -Dexec.mainClass="com.microsoft.playwright.CLI" -Dexec.args="install chromium"

# 2a. Run smoke tests only (~5 min, 34 tests)
mvn test -Dsmoke.only=true -Dtestng.suite.file=src/test/resources/testng-smoke.xml

# 2b. Run all 58 tests (9 suites: 4 UI + 4 API + 1 E2E)
mvn test

# 3. Generate and open Allure report
mvn allure:report
open target/site/allure-maven-plugin/index.html

# 4. Open local trend dashboard
open test-history/trend-dashboard.html
```

---

## Test Suites

| Suite | Type | Tests | Smoke | Target |
|---|---|---|---|---|
| Login Page | UI (Playwright) | 6 | 4 | saucedemo.com |
| Products Page | UI (Playwright) | 7 | 4 | saucedemo.com |
| Product Detail | UI (Playwright) | 6 | 4 | saucedemo.com |
| Add to Cart | UI (Playwright) | 5 | 3 | saucedemo.com |
| Checkout E2E | UI (Playwright) | 5 | 4 | saucedemo.com |
| User API | REST (RestAssured) | 7 | 4 | jsonplaceholder.typicode.com |
| Posts API | REST (RestAssured) | 8 | 4 | jsonplaceholder.typicode.com |
| Comments API | REST (RestAssured) | 6 | 3 | jsonplaceholder.typicode.com |
| Todos API | REST (RestAssured) | 8 | 4 | jsonplaceholder.typicode.com |
| **Total** | | **58** | **34** | |

---

## Architecture

```
User Story (.txt)
       │  [API MODE — requires ANTHROPIC_API_KEY]
       ▼
  TestCaseGenerator ──► ClaudeService ──► Claude API (claude-sonnet-4-6)
       │                                      │
       │                               TestCaseValidator
       │                               (schema + allow-lists + retry)
       │                                      │
       │                               AITestOutput_QualityScorer ──► AgentActivity
       │  [FILE MODE — default, no key needed]
       │
User Story (.json) ──────────────────────────┘
       │
       ▼
  BaseTest @DataProvider (smoke filter: -Dsmoke.only=true)
    ├── uiTestCases  ──► PlaywrightExecutor ──► Self-healing ──► Allure
    │                         │                     │
    │                   assert_accessible     SelfHealSuggester (Claude)
    └── apiTestCases ──► RestAssuredExecutor ──► AgentActivity ──► Allure
                              │                     │
                        schema_file            response_time_ms
                        (JSON Schema)          (SLA assertion)
                              │
                    QualityGateChecker (@AfterTest)
                              │
                    RunHistoryStore ──► TrendDashboard (4 charts)
```

**Key design decisions:**
- **JSON files contain only logical names** (`login_button`, `username_input`) — never CSS selectors. `PageRegistry` resolves them at runtime. This decouples test intent from implementation.
- **Self-healing at two levels:** step-level `fallback_targets` (in the JSON) + `PageRegistry` fallback chains (in Page Object classes). Both are tried before `SelfHealSuggester` is invoked.
- **AI repair is human-in-the-loop:** `SelfHealSuggester` attaches Claude's selector suggestions to Allure and writes them to a `.json` artifact — engineers review before applying. No blind auto-patch.
- **`@BeforeTest`/`@AfterTest` not `@BeforeSuite`/`@AfterSuite`** — each `<test>` block in `testng.xml` gets its own isolated lifecycle so Login and Checkout modules report independently.
- **Quality gate runs per module** — `QualityGateChecker` in `@AfterTest` enforces gates at the suite level, not globally, so a flaky suite is caught in isolation.
- **Smoke filter in DataProvider** — `BaseTest` checks `-Dsmoke.only=true` and filters each DataProvider list to tests tagged `"smoke"`. No extra runner code needed.

---

## Smoke-First CI Pipeline

```
push / PR to any branch
        │
        ▼
  ┌─────────────────────────────────┐
  │  Smoke Gate (34/58 tests)       │  fail-fast: true — cancel if any browser fails
  │  chromium | firefox | webkit    │  ~5 min wall-clock
  └──────────────┬──────────────────┘
                 │ all green?
                 ▼ (main push / schedule / manual dispatch only)
  ┌─────────────────────────────────┐
  │  Full Suite (58 tests)          │  fail-fast: false — independent browser results
  │  chromium | firefox | webkit    │  ~15 min wall-clock
  └──────────────┬──────────────────┘
                 ▼
  ┌─────────────────────────────────┐
  │  Quality Gate Check             │  pass rate ≥80%, flake rate ≤20%
  │  Merge + Allure Report          │  merged across 3 browsers
  │  Deploy to GitHub Pages (main)  │
  └─────────────────────────────────┘
```

| Trigger | Smoke | Full Suite |
|---|---|---|
| Push to any branch | Always | Never |
| Push to `main` | Always | If smoke passes |
| Pull request | Always | Never |
| Nightly schedule | Skipped | Always |
| Manual dispatch (`smoke`) | Yes | No |
| Manual dispatch (`full`) | Skipped | Always |
| Manual dispatch (`all`) | Yes | If smoke passes |

---

## Self-Healing in Action

When a locator times out, `PlaywrightExecutor` automatically tries fallbacks:

```
Primary: login_button  → [data-test='login-button1']  TIMEOUT
Fallback 1 (PageRegistry): [data-test='login-button']  ✓ HEALED
→ AgentActivity records self-heal event
→ Allure test tagged: self-healed:1
→ Console banner shows  Self-Heals: 5
```

If ALL fallbacks also fail, `SelfHealSuggester` kicks in:
```
All fallbacks exhausted for: .checkout-btn
→ DOM snapshot captured (6000 chars)
→ Claude API called: "suggest replacement selectors for .checkout-btn"
→ AI Repair Suggestion attached to Allure result
→ Suggestions saved to: test-history/repair-suggestions/{testId}.json (survives mvn clean; cached on next run)
→ Human reviews suggestions before applying — no blind auto-patch
```

Intentionally wrong selectors are set in `LoginPage.java` and `InventoryPage.java` so self-healing is exercised and visible on every run.

---

## AI Output Quality Scoring

Every test generation (API MODE or FILE MODE) is scored by `AITestOutput_QualityScorer` on 4 dimensions:

| Dimension | What it measures | Score contribution |
|---|---|---|
| **Assertion depth** | Variety of assertion types used (not just `assert_url_contains`) | 0–30 |
| **Negative coverage** | At least 1 test with invalid input or error scenario | 0–25 |
| **Edge case coverage** | At least 1 test targeting a boundary or unusual condition | 0–25 |
| **Step realism** | Step count in 3–12 range (too few = shallow, too many = hallucination risk) | 0–20 |

**Tier labels:** EXCELLENT (≥90) / GOOD (≥70) / FAIR (≥50) / POOR (≥25) / CRITICAL (<25)

Scores are recorded in `AgentActivity`, persisted in `test-history/runs.json`, and charted in the 4th panel of the trend dashboard. This enables:
- A/B comparison of different prompt strategies
- Alerting when AI generation quality degrades across runs
- Evidence-based prompt tuning decisions

---

## API Contract Validation

API suites validate response *structure*, not just field values:

```json
{ "type": "schema_file", "path": "", "expected": "user.schema.json" }
```

`RestAssuredExecutor` loads `src/main/resources/schemas/user.schema.json` from the classpath and validates the response body against it using `rest-assured-json-schema-validator`. Catches missing required fields, type mismatches, and structural regressions — without needing a Pact broker.

```json
{ "type": "response_time_ms", "path": "", "expected": "3000" }
```

`response_time_ms` assertions fail the test if the API response exceeds the threshold — SLA violations are caught at the test level, not monitored separately in production.

---

## Docker

**Full pipeline (enterprise pattern — smoke gate then parallel full suite):**
```bash
docker-compose up --build      # smoke → chromium-ui + firefox-ui + webkit-ui + api
./ci/merge-and-report.sh       # merges chromium + firefox + webkit + api → Allure report
open target/site/allure-maven-plugin/index.html
```

Pipeline stages:
1. `smoke` — Chromium, 34 smoke-tagged tests, fast gate (~5 min). All other containers wait.
2. `chromium-ui`, `firefox-ui`, `webkit-ui` — UI tests only (29 each), run in parallel after gate
3. `api` — 29 API tests, runs once (browser-agnostic), also starts after gate

Result: **116 unique test scenarios** in Allure = 29 UI × 3 browsers + 29 API × 1 (correct enterprise view).

**Single service overrides:**
```bash
docker-compose up --build chromium-ui   # UI tests, Chromium only
docker-compose up --build api           # API tests only
docker-compose up --build smoke         # Smoke gate only
```

**Single container (manual):**
```bash
docker build -t ai-test-generator .

docker run --rm -e BROWSER=firefox -e UI_ONLY=true -e LOGIN_PASS=secret_sauce \
  -v "$(pwd)/target:/app/target" ai-test-generator

docker run --rm -e API_ONLY=true \
  -v "$(pwd)/target:/app/target" ai-test-generator
```

**Secrets** — create `.env` in project root (gitignored):
```
ANTHROPIC_API_KEY=sk-ant-...
LOGIN_PASS=your_password
```

| Env var | Suite file |
|---|---|
| `SMOKE_ONLY=true` | `testng-smoke-docker.xml` |
| `UI_ONLY=true` | `testng-ui-docker.xml` |
| `API_ONLY=true` | `testng-api-docker.xml` |
| _(none)_ | `testng-docker.xml` (full, all 9 runners) |

---

## Architectural Decision Log

Ten decisions with the business rationale behind each:

| # | Decision | Rationale |
|---|---|---|
| 1 | **JSON-only output, no markdown fences** | Claude sometimes wraps output in ` ```json ``` ` — this breaks downstream parsing. Stripping fences is a smell; enforcing plain JSON in the prompt eliminates the problem at the source. |
| 2 | **3-layer validator + retry (not trust-and-run)** | AI output is probabilistic. Running unvalidated JSON through Playwright fails in unpredictable ways. Explicit schema check + retry with error context catches ~95% of format issues before any browser opens. |
| 3 | **FILE MODE as default (no API key required)** | Lowers barrier to evaluation — a reviewer can clone, run `mvn test`, and see all 58 tests pass without any account setup. API MODE is the production path; FILE MODE is the demo path. |
| 4 | **Self-healing at two levels, not one** | Step-level `fallback_targets` (defined by Claude per test) and `PageRegistry` fallback chains (maintained by engineers) serve different failure modes. Claude can suggest fallbacks when generating; engineers maintain structural fallbacks centrally. |
| 5 | **Smoke-first CI gate, not just smoke-only** | Running only smoke tests in CI is common but insufficient — it leaves the full suite as a manual step. The pipeline runs smoke on every push and full suite on merge to main, which is the right balance between speed and confidence. |
| 6 | **Serial Docker execution (not parallel) for Rosetta** | Root-cause analysis showed 7 distinct Chromium crash patterns under Rosetta x86 emulation with concurrent instances. `shm_size: 512mb` and serial execution together eliminate crashes. `testng-docker.xml` enforces `parallel="none"`. |
| 7 | **FlakeRetryAnalyzer retries TimeoutError only** | Retrying all failures masks real regressions. `TimeoutError` is infra flake; `AssertionError` is a product defect. The distinction matters — a framework that retries assertion failures will never catch a regression reliably. |
| 8 | **@BeforeTest/@AfterTest per module, not @BeforeSuite** | Each TestNG `<test>` block represents one suite (Login, Products, Checkout). `@BeforeTest` scope gives each module independent `AgentActivity` reset, quality score, and gate check — they don't pollute each other's metrics. |
| 9 | **AI repair is human-in-the-loop, not auto-apply** | Auto-applying Claude's selector suggestions creates a trust boundary problem — Claude may suggest a selector that matches the wrong element. Attaching suggestions to Allure and saving them as a `.json` artifact keeps the engineer in control. |
| 10 | **Quality gate at test level, not only in CI** | `QualityGateChecker` runs in `@AfterTest` and throws, which surfaces in the test report immediately. The CI check (`quality-gate-failure.txt`) is a second enforcement point — belt and suspenders. |

---

## Outputs After Each Run

```
target/
  allure-results/           ← raw Allure data (auto-cleaned before each run)
  agent-reports/
    {runId}-{Module}.html   ← styled HTML agent activity report (per suite)
    {runId}-{Module}.json   ← machine-readable (CI parseable)
  site/allure-maven-plugin/ ← generated Allure HTML report

test-history/               ← committed to git, survives mvn clean
  runs.json                 ← cumulative run history (last 50 entries, includes quality score)
  trend-dashboard.html      ← Chart.js dashboard, open in browser — no server needed
  agent-reports/            ← persistent HTML reports linked from dashboard
  repair-suggestions/       ← Claude's selector repair suggestions; cached to avoid redundant API calls
```

---

## Project Structure

```
src/
├── main/java/com/qa/ai/
│   ├── claude/
│   │   ├── ClaudeService.java        ← Anthropic SDK wrapper + retry
│   │   ├── PromptTemplates.java      ← All prompts — tunable here
│   │   └── SelfHealSuggester.java    ← Agentic repair: DOM → Claude → selector suggestions
│   ├── config/
│   │   ├── ConfigManager.java        ← config.properties singleton
│   │   ├── SensitiveDataMasker.java  ← Central secret-scrubbing utility
│   │   └── TestDataResolver.java     ← ${VAR} substitution
│   ├── executor/
│   │   ├── PlaywrightExecutor.java   ← UI execution, self-healing, accessibility, diagnostics
│   │   └── RestAssuredExecutor.java  ← API execution, schema validation, SLA assertions
│   ├── generator/
│   │   └── TestCaseGenerator.java    ← Orchestrates generation, validation, retry, scoring
│   ├── model/                        ← TestCase, TestSuite, TestStep, ApiRequest, ApiAssertion
│   ├── pages/
│   │   ├── BasePage.java
│   │   ├── LoginPage.java            ← primary + fallback selectors
│   │   ├── InventoryPage.java
│   │   ├── ProductDetailPage.java
│   │   ├── BurgerMenuComponent.java
│   │   └── PageRegistry.java         ← merged selector lookup for self-healing
│   ├── reporter/
│   │   ├── AgentActivity.java        ← thread-safe event collector + quality score
│   │   ├── AllureReporter.java       ← attachments, executor.json, history preservation
│   │   ├── QualityGateChecker.java   ← enforces pass rate ≥80%, flake rate ≤20%
│   │   ├── RunContext.java           ← UUID + timestamp per JVM run
│   │   ├── RunHistoryStore.java      ← persists to test-history/runs.json (incl. quality score)
│   │   └── TrendDashboard.java       ← generates 4-chart Chart.js HTML dashboard
│   ├── scorer/
│   │   ├── AITestOutput_QualityScorer.java    ← scores Claude's JSON output at generation time (0–100)
│   │   └── TestEffectivenessScorer.java       ← scores runtime performance in @AfterTest (0–100)
│   └── validator/
│       └── TestCaseValidator.java    ← schema + allow-list validation
└── test/java/com/qa/ai/
    ├── base/BaseTest.java            ← shared @DataProvider (smoke filter), @Test, lifecycle
    ├── retry/
    │   ├── FlakeRetryAnalyzer.java
    │   └── RetryAnnotationTransformer.java
    ├── runner/
    │   ├── AITestRunner_LoginPageTests.java
    │   ├── AITestRunner_ProductsPageTests.java
    │   ├── AITestRunner_ProductDetailTests.java
    │   ├── AITestRunner_AddToCartTests.java
    │   ├── AITestRunner_CheckoutE2ETests.java  ← full login→browse→checkout→confirm journey
    │   ├── AITestRunner_UserApiTests.java
    │   ├── AITestRunner_PostsApiTests.java
    │   ├── AITestRunner_CommentsApiTests.java
    │   └── AITestRunner_TodosApiTests.java
    └── unit/
        ├── TestCaseValidatorTest.java      ← 14 tests: valid JSON, disallowed actions, XSS inputs
        └── SensitiveDataMaskerTest.java    ← 17 tests: key detection, scrubJson, scrubFormEncoded
```

---

## Adding a New Test Runner

Designed for team adoption — any team member can add a new suite in under 5 minutes:

1. Create a pre-generated JSON story in `src/main/resources/stories/generated/`
2. Create a 3-line runner subclass:

```java
public class AITestRunner_CheckoutTests extends BaseTest {
    private static final String STORY_FILE = "src/main/resources/stories/generated/checkout-ui-tests.json";
    private static final String BASE_URL   = "https://www.saucedemo.com";
    @Override protected String getStoryFilePath() { return STORY_FILE; }
    @Override protected String getBaseUrl()       { return BASE_URL; }
    @Override protected String getModuleName()    { return "Checkout"; }
}
```

3. Add a `<test>` block to the suite XML files. Done — `@DataProvider`, `@Test`, lifecycle, quality scoring, and quality gate all inherited.
4. Tag key test cases with `"smoke"` in the JSON so they're included in the fast gate.

---

## Suite Files

| File | Use case |
|---|---|
| `testng.xml` | Local / native CI — all 9 runners, parallel (thread-count=3) |
| `testng-smoke.xml` | Smoke gate, native CI — all 9 runners, parallel, `-Dsmoke.only=true` |
| `testng-docker.xml` | Docker full suite — all 9 runners, serial (Rosetta stability) |
| `testng-smoke-docker.xml` | Docker smoke gate — all 9 runners, serial, `SMOKE_ONLY=true` |
| `testng-ui-docker.xml` | Docker UI containers — 5 Playwright runners only, serial |
| `testng-api-docker.xml` | Docker API container — 4 RestAssured runners only, parallel |

---

## API Mode (live AI generation)

```bash
export ANTHROPIC_API_KEY=sk-ant-...
# Change STORY_FILE in the runner to a .txt path, then:
mvn test
```

Claude generates fresh test cases on each run. The validator rejects non-conforming JSON and retries once with the error context fed back to Claude. The generated suite is scored by `AITestOutput_QualityScorer` and the score is recorded to the trend dashboard.

Available story files in `src/main/resources/stories/`:
- `login-story.txt`, `checkout-e2e-story.txt`, `product-detail-story.txt`, `add-to-cart-story.txt`
- `posts-api-story.txt`, `comments-api-story.txt`, `todos-api-story.txt`

---

## Configuration

| File | Purpose |
|---|---|
| `src/main/resources/config.properties` | Browser, headless flag, timeout, retry count, quality gate thresholds |
| `src/main/resources/test-data.properties` | `${LOGIN_USER}`, `${LOGIN_PASS}`, `${BASE_URL}` etc. — substituted at runtime |
| `src/test/resources/testng.xml` | Full suite, parallel (thread-count=3) |
| `src/test/resources/testng-smoke.xml` | Smoke gate, parallel — used with `-Dsmoke.only=true` |
| `src/test/resources/testng-docker.xml` | Docker full suite, serial — prevents Rosetta x86 crashes |
| `src/test/resources/testng-smoke-docker.xml` | Docker smoke gate, serial |

**Quality gate config** (in `config.properties`):
```properties
quality.gate.min.pass.pct=80
quality.gate.max.flake.pct=20
```

---

## CI / GitHub Actions

`.github/workflows/ci.yml` — smoke-first pipeline:
- **Smoke gate** — runs on every push/PR across all 3 browsers (`fail-fast: true`)
- **Full suite** — runs on main push (if smoke passed), nightly schedule, or manual dispatch
- **Quality gate check** — CI step fails explicitly if `target/quality-gate-failure.txt` exists
- **Report** — merges 3-browser results, generates Allure HTML, deploys to GitHub Pages on main

Set `LOGIN_PASS` (required) and `ANTHROPIC_API_KEY` (API mode only) as Actions secrets.

### Pipeline in Action

![CI Pipeline Overview](docs/screenshots/gitHubActions-CI_Pipeline.png)

![CI Job Detail — Full Tests / Chromium](docs/screenshots/gitHubActions-CI.png)

---

## Troubleshooting

**`Playwright browser not found`**
```bash
mvn exec:java -Dexec.mainClass="com.microsoft.playwright.CLI" -Dexec.args="install chromium"
```

**`Claude failed to produce valid JSON after 2 attempts`**
Check `target/logs/ai-test-generator.log` for the raw Claude response and validator error.

**Tests fail with unexpected selectors**
`LoginPage.java` and `InventoryPage.java` contain intentionally wrong primary selectors to demonstrate self-healing. This is by design — fallbacks in `PageRegistry` recover them.

**Docker Chromium "Target crashed" on Apple Silicon**
Docker uses `testng-docker.xml` (serial execution) to prevent Chromium x86 crashes under Rosetta.

**Quality gate failure in CI**
Check `target/quality-gate-failure.txt` for the specific threshold breached. Adjust `quality.gate.min.pass.pct` in `config.properties` if the threshold is too aggressive for the current suite.

---

## Tech Stack

| Dependency | Version |
|---|---|
| Java | 17 |
| Anthropic Java SDK | 2.30.0 |
| Playwright Java | 1.44.0 |
| RestAssured | 5.4.0 |
| REST Assured JSON Schema Validator | 5.4.0 |
| TestNG | 7.10.2 |
| Allure TestNG | 2.27.0 |
| Jackson | 2.17.1 |
| Log4j2 | 2.23.1 |

---

## License

[MIT](LICENSE)
