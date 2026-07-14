# Future Improvements

## 1. Historical Flakiness Tracking
**Gap:** A test that passes 9 runs and fails 1 is never identified as flaky. `RunHistoryStore` records suite-level totals but not per-test results across runs.
**Fix:** Store per-test pass/fail per run in `runs.json`. After each suite, query the last N runs and flag tests with failure rate between 5–50% as flaky in the Allure report and trend dashboard. No new dependencies needed — `RunHistoryStore` already owns the persistence layer.

---

## 2. `RunHistoryStore` JSON Parsing via Regex
**Gap:** `RunHistoryStore.loadExistingEntries()` uses `Pattern.compile("\\{[^}]+}")` to parse `runs.json`. Breaks silently if any string value contains `}`.
**Fix:** Replace with Jackson `ObjectMapper` (already a project dependency). One class change, no new dependencies. Eliminates the fragile regex and enables typed deserialization.

---

## 3. Quality Scorer Checks Tags, Not Behaviour
**Gap:** `AITestOutput_QualityScorer` awards 25/25 for negative coverage if any test has `"negative"` in a tag or title. Claude can satisfy this without the steps actually asserting an error state.
**Fix:** Add a behavioural check: a negative test must contain at least one `assert_text` or `assert_visible` step targeting an error element (e.g. target contains `error`, `alert`, `message`). Score 25 only if the assertion is present, not just the tag.

---

## 4. `assert_accessible` Uses a Hand-Rolled Audit, Not axe-core
**Gap:** The current accessibility check is a basic in-house JS audit (title, landmarks, alt text, form labels). Misses the vast majority of WCAG 2.1 AA violations.
**Fix:** Inject axe-core via `page.addScriptTag()` from a CDN or bundled resource, run `axe.run()`, parse the violations JSON, and attach it to Allure. No new Java dependencies. Axe-core returns structured violation data including impact level, WCAG criteria, and affected elements.

---

## 5. Claude API — Single Retry, No Backoff
**Gap:** `TestCaseGenerator` retries Claude exactly once on validation failure. No distinction between a validation error (bad output) vs a transient network or rate-limit error. No exponential backoff.
**Fix:** Wrap `ClaudeService.generateTestCases()` in a retry loop with exponential backoff (e.g. 1s → 2s → 4s). Catch `AnthropicException` subtypes separately: re-throw immediately on validation failure (Claude error, not transient), back off and retry on rate-limit or 5xx. Cap at 3 total attempts.

---

## 6. `MAX_RUNS = 50` Drops History Silently
**Gap:** When `runs.json` exceeds 50 entries, oldest runs are trimmed with no warning. Trend charts lose data without any indication to the viewer.
**Fix:** Two options: (a) increase `MAX_RUNS` and add a warning banner to `TrendDashboard` when entries are close to the cap; (b) archive dropped entries to `test-history/runs-archive.json` instead of deleting them. The dashboard can optionally load the archive for extended trend views.

---

## 7. PageRegistry — True Auto-Discovery
**Gap:** `PageRegistry.PAGES` is a hardcoded list. A safety net test (`PageRegistryValidationTest`) now catches unregistered pages at build time, but registration is still manual.
**Fix:** Add the `Reflections` library (`org.reflections:reflections:0.10.2`). Replace `PAGES` with a classpath scan: `new Reflections("com.qa.ai.pages").getSubTypesOf(BasePage.class)`. Filter abstract classes, instantiate each via `getDeclaredConstructor().newInstance()`. Zero manual steps on new page addition.

---

## 8. Trend Dashboard — Effectiveness Score Not Yet Charted
**Gap:** `TestEffectivenessScorer` records a runtime effectiveness score but `TrendDashboard` only charts the AI output quality score. The two scores are not compared side by side over time.
**Fix:** Add `effectivenessScore` to `RunHistoryStore` entries and add a sixth Chart.js panel to `TrendDashboard` overlaying both scores per run. A diverging gap between output quality and effectiveness score is a signal that generated tests are structurally sound but failing in practice — valuable for prompt tuning decisions.
