# How to Generate Test Cases from a User Story

This guide walks through the complete manual workflow for generating executable test cases
using the claude.ai chat interface (File Mode — no API key required).

---

## Overview

The framework supports two modes. This guide covers **File Mode**, which is the default
and requires no API key or credits.

```
You write a user story (.txt)
        ↓
Paste system prompt + story into claude.ai
        ↓
Claude returns a JSON test suite
        ↓
Save JSON to src/main/resources/stories/generated/
        ↓
Run mvn test — Playwright/RestAssured executes the tests
```

---

## Step 1 — Write Your User Story

Create a `.txt` file under `src/main/resources/stories/`. A good story includes:

- **Role + goal** — one sentence in "As a ... I want to ..." format
- **Site/API URL** — the exact base URL under test
- **UI elements or API endpoints** — confirmed selectors or endpoint paths with response shapes
- **Test accounts or test data** — exact values to use
- **Acceptance criteria** — the specific behaviours to verify

**Example — UI story** (`src/main/resources/stories/login-story.txt`):

```
As a customer, I want to log in to the Sauce Demo e-commerce site
so that I can browse and add products to my cart.

SITE URL: https://www.saucedemo.com

UI ELEMENTS (confirmed selectors):
  Login page:
    Username input : #user-name
    Password input : #password
    Login button   : #login-button
    Error message  : [data-test="error"]

  Inventory page (after login):
    Page title     : .title
    Product list   : .inventory_list

TEST ACCOUNTS:
  Valid user   : standard_user / secret_sauce
  Locked user  : locked_out_user / secret_sauce  (should be rejected)

ACCEPTANCE CRITERIA:
  - Logging in with standard_user redirects to /inventory.html
  - The inventory page title shows "Products"
  - Logging in with locked_out_user shows error containing "locked out"
  - Logging in with wrong password shows error containing "do not match"
```

**Example — API story** (`src/main/resources/stories/user-api-story.txt`):

```
As a system integrator, I want to interact with the User API
so that I can retrieve, create, and manage user records reliably.

SYSTEM UNDER TEST: https://jsonplaceholder.typicode.com

API ENDPOINTS:
  GET  /users        — list all users. Returns: [{ id, name, username, email }]
  GET  /users/{id}   — get single user. Returns: { id, name, username, email }
  GET  /users/9999   — non-existent user. Returns: HTTP 404

ACCEPTANCE CRITERIA:
  - Listing users returns HTTP 200 with a non-empty array
  - First user has a non-empty name and email
  - Getting user ID=2 returns HTTP 200 with id=2 and email present
  - Getting user ID=9999 returns HTTP 404
  - Creating a new user via POST /users returns HTTP 201 with a generated id

NOTES:
  - No authentication required
  - All requests should include Accept: application/json header
```

---

## Step 2 — Build the Prompt to Paste into claude.ai

The prompt has two parts: a **system prompt** (tells Claude the rules) followed by your
**user story**. Paste them together as a single message.

### Part A — System Prompt (copy exactly as-is)

```
You are an expert QA test engineer who writes precise, executable test cases.

OUTPUT RULES — strictly enforced:
• Output ONLY valid JSON. No markdown code fences, no preamble, no explanation.
• The root JSON object must have exactly ONE key: "tests" (an array).
• Generate between 3 and 8 test cases.
• Each test case covers a distinct scenario (happy path, negative, edge case).

EACH TEST CASE OBJECT must include these exact fields:
{
  "id":       string  — unique, e.g. "TC001"
  "title":    string  — clear action + expected outcome
  "type":     string  — EXACTLY "ui" OR "api" (no other values)
  "tags":     array   — 1–3 strings, e.g. ["smoke", "login"]
  "expected": string  — one sentence, the expected outcome
}

FOR type="ui" ONLY — also include:
  "steps": array of step objects:
  {
    "action": "...",
    "target": "...",
    "value": "...",
    "fallback_targets": ["...", "..."]   (OPTIONAL — alternative selectors if primary times out)
  }

ALLOWED ACTIONS (use ONLY these exact strings):
  navigate             target=URL, no value
  fill                 target=CSS selector, value=text to enter
  click                target=CSS selector
  assert_visible       target=CSS selector
  assert_text          target=CSS selector, value=expected text
  assert_url_contains  target=URL fragment string
  wait_for             target=CSS selector
  select               target=CSS selector, value=option text
  assert_accessible    target=ignored (leave empty), no value — checks page a11y tree

SELECTOR RULES for UI tests:
  • Use only: #id, .class, [attribute=value], tag selectors
  • Do NOT invent selectors. Only use selectors clearly shown in the story context.
  • Prefer #id selectors when the story mentions element IDs.
  • If you know a reliable alternative selector, add it to "fallback_targets".
  • Tests run at viewport 1280×800 — do not target elements that only exist at mobile breakpoints.

TEST DATA & SECURITY RULES (STRICTLY ENFORCED):
  • NEVER hardcode passwords, tokens, API keys, secrets, or any credentials
    directly in test step values, request bodies, or headers.
  • Use ${VAR} placeholders for ALL sensitive data. Placeholders are resolved
    at runtime from environment variables (first) or test-data.properties.
    Sensitive values are automatically masked as [MASKED] in all Allure reports,
    HAR files, and activity logs — so hardcoded values serve no purpose and
    are a security violation.
  • The following key patterns are treated as sensitive and masked in reports:
      password, passwd, secret, token, api_key, apikey, authorization, auth
  • Available variables:
      ${LOGIN_USER}    — test username
      ${LOGIN_PASS}    — test password  (ALWAYS use this, never hardcode)
      ${BASE_URL}      — UI base URL
      ${API_BASE_URL}  — API base URL
  • For API tests requiring auth headers:
      "headers": {"Authorization": "${AUTH_TOKEN}"}   ← correct
      "headers": {"Authorization": "Bearer abc123"}   ← FORBIDDEN
  • Example UI step: {"action":"fill","target":"#password","value":"${LOGIN_PASS}"}

FOR type="api" ONLY — also include:
  "request": {
    "method": "GET|POST|PUT|PATCH|DELETE",
    "url": "full URL",
    "params": {},      (query params map, can be empty)
    "headers": {},     (header map — use ${VAR} for any auth/token headers)
    "body": ""         (JSON string for POST/PUT/PATCH — use ${VAR} for sensitive fields)
  }
  "assertions": array of: {"type": "...", "path": "...", "expected": "..."}

ALLOWED ASSERTION TYPES:
  status_code      path="" expected=integer as string e.g. "200"
  json_path        path=JsonPath expression, expected=value as string
  header           path=header name, expected=expected value
  body_contains    path="", expected=substring to find in body
  not_empty        path=JsonPath expression, expected="true"
  schema_file      path="", expected=filename e.g. "user.schema.json" (validates against JSON Schema)
  response_time_ms path="", expected=threshold in ms e.g. "2000" (asserts response SLA)

VALIDATION: Your output will be machine-validated. Any deviation from this schema
causes an immediate retry. Ensure JSON is syntactically perfect.
```

### Part B — Append your user story

After the system prompt, add:

```
Generate test cases for the following user story.

BASE URL: https://www.saucedemo.com

USER STORY:
---
[paste your .txt story here]
---

Cover: happy path, at least one negative scenario (invalid input or missing data),
and at least one edge case.

Output only the JSON object. No other text.
```

Replace `https://www.saucedemo.com` with your actual base URL and paste the full
contents of your `.txt` file where indicated.

---

## Step 3 — Get the JSON from Claude

Paste the combined prompt into [claude.ai](https://claude.ai) and send it.

Claude will respond with a raw JSON object. It should start with `{` and have this shape:

```json
{
  "tests": [
    {
      "id": "TC001",
      "title": "Valid login redirects to inventory page",
      "type": "ui",
      "tags": ["smoke", "login"],
      "expected": "User is redirected to /inventory.html after successful login",
      "steps": [
        { "action": "navigate",            "target": "${BASE_URL}",    "value": "" },
        { "action": "fill",                "target": "username_input", "value": "${LOGIN_USER}" },
        { "action": "fill",                "target": "password_input", "value": "${LOGIN_PASS}" },
        { "action": "click",               "target": "login_button",   "value": "" },
        { "action": "assert_url_contains", "target": "/inventory.html","value": "" }
      ]
    },
    ...
  ]
}
```

**If Claude wraps the JSON in markdown fences** (` ```json ... ``` `), strip them — paste
only the content between the fences. The framework expects raw JSON.

**If the output looks wrong or incomplete**, reply to Claude with:

```
Fix the JSON. Output ONLY the corrected JSON object — no explanation, no markdown fences.
```

---

## Step 4 — Validate the JSON Before Saving (optional but recommended)

Paste the JSON into [jsonlint.com](https://jsonlint.com) or run:

```bash
echo '<paste json here>' | python3 -m json.tool
```

Check for:
- Root key is `"tests"` (array)
- Every test has: `id`, `title`, `type`, `tags`, `expected`
- UI tests have `steps[]`; each step has `action`, `target`, `value`
- API tests have `request{}` and `assertions[]`
- No hardcoded passwords — sensitive values use `${LOGIN_PASS}`, `${AUTH_TOKEN}`, etc.
- All `action` values are from the allowed list (navigate, fill, click, assert_visible,
  assert_text, assert_url_contains, wait_for, select, assert_accessible)
- All `type` values in assertions are from the allowed list (status_code, json_path,
  header, body_contains, not_empty, schema_file, response_time_ms)

The framework's `TestCaseValidator` will also validate on load and throw a clear error
if anything is wrong — so this step is just a time-saver.

---

## Step 5 — Save the JSON

Save the JSON to `src/main/resources/stories/generated/` using a descriptive name:

```
src/main/resources/stories/generated/login-ui-tests.json
src/main/resources/stories/generated/user-api-tests.json
src/main/resources/stories/generated/checkout-e2e-tests.json
```

---

## Step 6 — Wire Up a Test Runner

If this is a new story (not replacing an existing one), create a runner subclass in
`src/test/java/com/qa/ai/runners/`:

```java
public class AITestRunner_LoginPageTests extends BaseTest {
    private static final String STORY_FILE = "src/main/resources/stories/generated/login-ui-tests.json";
    private static final String BASE_URL   = "https://www.saucedemo.com";

    @Override protected String getStoryFilePath() { return STORY_FILE; }
    @Override protected String getBaseUrl()       { return BASE_URL; }
    @Override protected String getModuleName()    { return "Login Page"; }
}
```

Then add a `<test>` block to `src/test/resources/testng.xml`:

```xml
<test name="Login Page Tests" preserve-order="true">
    <classes>
        <class name="com.qa.ai.runners.AITestRunner_LoginPageTests"/>
    </classes>
</test>
```

No other code needed — `@DataProvider`, `@Test`, quality scoring, self-healing, and
Allure reporting are all inherited from `BaseTest`.

If you are **replacing** an existing JSON file, no runner changes are needed — just
overwrite the file and re-run.

---

## Step 7 — Run the Tests

```bash
# Run all tests
mvn test

# Run smoke-tagged tests only (~5 min)
mvn test -Dsmoke.only=true -Dtestng.suite.file=src/test/resources/testng-smoke.xml

# Run a specific runner only
mvn test -Dtest=AITestRunner_LoginPageTests

# Generate Allure HTML report after tests complete
mvn allure:report

# Open live Allure report in browser
mvn allure:serve
```

---

## What Happens at Runtime

Once `mvn test` runs, the framework takes over completely:

```
mvn test
  └── BaseTest.warmUp() (@BeforeTest)
        └── TestCaseGenerator.generateFromJson()  ← reads your saved .json
              └── TestCaseValidator               ← validates schema + allow-lists
                    └── TestQualityScorer         ← scores AI output quality (0–100)

  └── @DataProvider feeds each test case to @Test in parallel

  └── For each UI test case:
        PlaywrightExecutor
          ├── Resolves logical names → CSS selectors via PageRegistry
          ├── Executes steps (navigate, fill, click, assert...)
          ├── On selector timeout → tries fallback_targets, then PageRegistry fallbacks
          ├── If all fallbacks fail → SelfHealSuggester asks Claude for repair suggestions
          └── Attaches per-step screenshots, DOM snapshot, HAR to Allure

  └── For each API test case:
        RestAssuredExecutor
          ├── Sends HTTP request with headers/body
          ├── Evaluates assertions (status_code, json_path, schema_file, response_time_ms...)
          └── Attaches request, response, cURL command to Allure

  └── BaseTest.reportAgentActivity() (@AfterTest)
        ├── QualityGateChecker  ← fails suite if pass rate < 80% or flake rate > 20%
        ├── RunHistoryStore     ← appends run summary to test-history/runs.json
        └── TrendDashboard      ← regenerates test-history/trend-dashboard.html
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `JSON file failed validation` | Malformed JSON or disallowed action/assertion type | Check the error message — it names the exact field. Fix in the JSON file and re-run. |
| `Cannot read file` | Wrong path in `STORY_FILE` | Verify the path matches exactly, including the `generated/` subfolder. |
| `TimeoutError` on a step | Selector not found on page | Add a `fallback_targets` array to that step with alternative selectors. |
| `ANTHROPIC_API_KEY not set` | Runner is pointing at a `.txt` file instead of `.json` | Either set the env var for API Mode, or save a `.json` and point `STORY_FILE` at it. |
| Claude returned markdown fences | Claude ignored the output rules | Strip the fences manually, or reply asking for raw JSON only. |
| `QualityGateException` | Pass rate below 80% | Check Allure report for failing tests. Fix selectors or test data, regenerate if needed. |

---

## Real Examples in This Project

| User story | Generated test cases | Runner |
|---|---|---|
| `stories/login-story.txt` | `generated/login-ui-tests.json` (6 UI tests) | `AITestRunner_LoginPageTests` |
| `stories/user-api-story.txt` | `generated/user-api-tests.json` (5 API tests) | `AITestRunner_UserApiTests` |
| `stories/checkout-e2e-story.txt` | `generated/checkout-e2e-tests.json` (E2E tests) | `AITestRunner_CheckoutE2ETests` |
| `stories/posts-api-story.txt` | `generated/posts-api-tests.json` (API tests) | `AITestRunner_PostsApiTests` |

Open any `.txt` / `.json` pair side by side to see exactly how a story maps to
executable test cases.
