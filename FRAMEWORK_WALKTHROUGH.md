# Framework Walkthrough — AI Test Generator

## The Problem I Set Out to Solve

Traditional QA pipelines are reactive: engineers write test cases manually after features ship, selectors break silently when UI changes, and there's no objective measure of whether the tests are actually good. I built this framework to make test generation, execution, self-healing, and quality measurement autonomous and measurable.

---

## How It Works — The Two Paths

**File Mode (default — no API key needed)**
A human writes a plain-English user story → pastes the system prompt + story into claude.ai → saves Claude's JSON response → `mvn test` executes it. This is the demo path: anyone can clone and run 58 tests without any account setup.

**API Mode (production path)**
The same story file is passed to `ClaudeService`, which calls `claude-sonnet-4-6` via the Anthropic Java SDK. If Claude's output fails schema validation, the error is fed back to Claude for one retry. Both paths produce the same `TestSuite` object and execute identically.

---

## Key Architectural Decisions

**Why JSON as the intermediate format?**
It decouples Claude from the executors. Claude doesn't know Playwright or RestAssured — it only knows the JSON contract. The contract is enforced by `TestCaseValidator` with an explicit allow-list of actions and assertion types. This is the hallucination guardrail: Claude can't invent a `execute_script` action because the validator rejects it before it reaches the browser.

**Why logical names instead of raw CSS selectors in the JSON?**
Test cases reference `login_button`, not `#login-button`. `PageRegistry` resolves names to selectors at runtime. This decouples test intent from selector implementation — when a selector changes, you update one Page Object, not every test case.

**Self-healing at two levels**
When a selector fails: (1) step-level `fallback_targets` in the JSON are tried, then (2) `PageRegistry` fallback chains. If both are exhausted, `SelfHealSuggester` sends the live DOM snapshot to Claude and returns ranked selector suggestions as an Allure attachment. Suggestions are human-reviewed — not auto-applied.

**Two quality scores, not one**
`AITestOutput_QualityScorer` runs at generation time and scores Claude's output structure (assertion depth, negative coverage, edge cases, step realism). `TestEffectivenessScorer` runs in `@AfterTest` and scores actual runtime performance (pass rate, selector stability, flake resistance, selector coverage). The distinction matters: a suite can score 91/100 on output quality and still fail at runtime if selectors are stale.

**Quality gate as a hard stop**
`QualityGateChecker` in `@AfterTest` enforces ≥80% pass rate and ≤20% flake rate per suite. On breach it writes `target/quality-gate-failure.txt` and throws `QualityGateException` — both the JVM and the CI pipeline check independently for belt-and-suspenders enforcement.

---

## What Makes It Production-Grade

| Concern | How addressed |
|---|---|
| Hallucination | 3-layer defence: system prompt allow-lists → JSON schema validator → retry with error context |
| Secrets in reports | `SensitiveDataMasker` scrubs all headers, bodies, HAR, cookies before attaching to Allure |
| Flaky tests | `FlakeRetryAnalyzer` retries only on `TimeoutError` — not assertion failures |
| Selector drift | Two-level self-healing + Claude-assisted repair suggestions cached in `test-history/` |
| CI enforcement | Smoke gate (34 tests, 3 browsers, ~5 min) blocks full suite on failure; quality gate file check on every job |
| Trend visibility | `RunHistoryStore` + `TrendDashboard` persist pass/fail, self-heals, quality scores across runs in Chart.js dashboard |

---

## The Stack

`Claude API (Anthropic Java SDK)` → `TestNG @DataProvider` → `Playwright 1.44` (UI) / `RestAssured 5.4` (API) → `Allure 2.27` → `GitHub Actions` (smoke-first, 3-browser matrix)
