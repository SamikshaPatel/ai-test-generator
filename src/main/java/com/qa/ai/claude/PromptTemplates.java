package com.qa.ai.claude;

/**
 * All Claude prompts live here — centralised for easy tuning and portfolio demo.
 *
 * HALLUCINATION GUARDRAILS baked into the prompts:
 *   1. Strict JSON-only output (no markdown fences)
 *   2. Explicit action allow-list for UI steps
 *   3. Explicit assertion type allow-list for API tests
 *   4. "Do NOT invent selectors" instruction
 *   5. Max test count cap to keep output predictable
 *   6. Retry prompt feeds the validation error back to Claude
 *   7. Sensitive data security — ${VAR} required for all credentials/tokens/secrets;
 *      hardcoded secrets are forbidden and will be masked/rejected at runtime
 */
import java.util.Set;
import java.util.TreeSet;

public final class PromptTemplates {

    private PromptTemplates() {}

    // -------------------------------------------------------------------------
    // SYSTEM PROMPT — sent on every request
    // -------------------------------------------------------------------------

    /**
     * Overload that injects available Page Object logical names so Claude uses
     * them instead of inventing raw CSS selectors.
     */
    public static String systemPrompt(Set<String> availableLocators) {
        String locatorList = availableLocators.isEmpty()
            ? "  (none registered yet)"
            : new TreeSet<>(availableLocators).stream()
                .map(n -> "  " + n)
                .collect(java.util.stream.Collectors.joining("\n"));
        return systemPrompt() + """

                PAGE OBJECT LOCATOR REGISTRY (REUSE THESE — do not invent raw CSS):
                """ + locatorList + """

                REUSABILITY RULES:
                  • ALWAYS prefer a logical name from the registry above over a raw CSS selector.
                  • If an element you need is NOT in the registry, use its raw CSS selector AND
                    note it as "NEW_LOCATOR: <description>" in a comment field if supported.
                  • For repeated precondition sequences (e.g., login steps), reuse the same
                    logical names across all test cases — do not vary them per test.
                """;
    }

    public static String systemPrompt() {
        return """
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
                  • Tests run at viewport 1280×800 — do not target elements that only exist at
                    mobile breakpoints.

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
                """;
    }

    // -------------------------------------------------------------------------
    // USER PROMPT — first attempt
    // -------------------------------------------------------------------------
    public static String userPrompt(String userStory, String baseUrl) {
        return String.format("""
                Generate test cases for the following user story.

                BASE URL: %s

                USER STORY:
                ---
                %s
                ---

                Cover: happy path, at least one negative scenario (invalid input or missing data),
                and at least one edge case.

                Output only the JSON object. No other text.
                """, baseUrl, userStory);
    }

    // -------------------------------------------------------------------------
    // RETRY PROMPT — sent when validation fails; feeds the error back to Claude
    // -------------------------------------------------------------------------
    public static String retryPrompt(String userStory, String baseUrl, String validationError) {
        return String.format("""
                Your previous response failed JSON validation with this error:

                ERROR: %s

                Fix the JSON and regenerate test cases for the story below.
                Output ONLY the corrected JSON object — no explanation.

                REMINDERS:
                  • Never hardcode passwords, tokens, or secrets — use ${VAR} placeholders.
                  • Sensitive values (password, token, auth, secret) are masked in reports;
                    hardcoding them is both a security violation and non-functional.

                BASE URL: %s

                USER STORY:
                ---
                %s
                ---
                """, validationError, baseUrl, userStory);
    }
}
