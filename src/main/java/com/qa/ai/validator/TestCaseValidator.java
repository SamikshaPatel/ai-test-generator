package com.qa.ai.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qa.ai.model.TestCase;
import com.qa.ai.model.TestSuite;
import com.qa.ai.pages.PageRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates the JSON Claude produces before we try to execute anything.
 *
 * This is the primary hallucination mitigation layer:
 *   • Ensures root object has "tests" array
 *   • Validates every test case has required fields
 *   • Checks "type" is "ui" or "api" (not some invented value)
 *   • Checks UI tests have "steps" and API tests have "request"
 *   • Validates each step has an allowed "action" value
 *   • Validates each assertion has an allowed "type" value
 *
 * On failure, returns a human-readable error string that the generator
 * feeds back to Claude in a retry prompt.
 */
public class TestCaseValidator {

    private static final Logger log = LogManager.getLogger(TestCaseValidator.class);

    private static final Set<String> ALLOWED_ACTIONS = Set.of(
            "navigate", "fill", "click", "assert_visible", "assert_text",
            "assert_url_contains", "wait_for", "select",
            "assert_accessible"   // Playwright a11y snapshot — verifies accessibility tree is non-null
    );

    private static final Set<String> ALLOWED_ASSERTION_TYPES = Set.of(
            "status_code", "json_path", "header", "body_contains", "not_empty",
            "schema_file",      // validates response against a JSON Schema file in src/main/resources/schemas/
            "response_time_ms"  // asserts response time is below a threshold in milliseconds
    );

    private final ObjectMapper mapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // VALIDATION ENTRY POINT
    // -------------------------------------------------------------------------

    /**
     * Validates raw JSON and maps it to a TestSuite if valid.
     *
     * @param rawJson raw string from Claude
     * @return ValidationResult with either a populated TestSuite or an error message
     */
    public ValidationResult validate(String rawJson) {
        log.debug("Validating Claude JSON response ({} chars)", rawJson.length());

        // Step 1 — Is it parseable JSON?
        JsonNode root;
        try {
            root = mapper.readTree(rawJson);
        } catch (Exception e) {
            return ValidationResult.failure("Invalid JSON syntax: " + e.getMessage());
        }

        // Step 2 — Does it have the "tests" array?
        if (!root.has("tests") || !root.get("tests").isArray()) {
            return ValidationResult.failure(
                    "Root JSON must have a 'tests' array. Found keys: " + root.fieldNames());
        }

        JsonNode testsNode = root.get("tests");
        if (testsNode.isEmpty()) {
            return ValidationResult.failure("'tests' array is empty. Generate at least 3 test cases.");
        }

        // Step 3 — Validate each test case
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < testsNode.size(); i++) {
            JsonNode tc = testsNode.get(i);
            errors.addAll(validateTestCase(tc, i));
        }

        if (!errors.isEmpty()) {
            String errorSummary = String.join("; ", errors);
            log.warn("Validation failed with {} error(s): {}", errors.size(), errorSummary);
            return ValidationResult.failure(errorSummary);
        }

        // Step 4 — Map to TestSuite
        try {
            TestSuite suite = mapper.treeToValue(root, TestSuite.class);
            log.info("Validation passed. {} test cases: {} UI, {} API",
                    suite.totalCount(), suite.uiCount(), suite.apiCount());
            return ValidationResult.success(suite);
        } catch (Exception e) {
            return ValidationResult.failure("Mapping error after schema validation: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // PRIVATE VALIDATION HELPERS
    // -------------------------------------------------------------------------

    private List<String> validateTestCase(JsonNode tc, int index) {
        List<String> errors = new ArrayList<>();
        String prefix = "tests[" + index + "]";

        // Required base fields
        validateRequiredString(tc, "id",       prefix, errors);
        validateRequiredString(tc, "title",    prefix, errors);
        validateRequiredString(tc, "expected", prefix, errors);

        // Validate "type"
        if (!tc.has("type") || !tc.get("type").isTextual()) {
            errors.add(prefix + ".type is missing or not a string");
            return errors; // can't continue without knowing type
        }

        String type = tc.get("type").asText();
        if (!"ui".equals(type) && !"api".equals(type)) {
            errors.add(prefix + ".type must be 'ui' or 'api', got: '" + type + "'");
            return errors;
        }

        // Type-specific validation
        if ("ui".equals(type)) {
            validateUiTestCase(tc, prefix, errors);
        } else {
            validateApiTestCase(tc, prefix, errors);
        }

        return errors;
    }

    private void validateUiTestCase(JsonNode tc, String prefix, List<String> errors) {
        if (!tc.has("steps") || !tc.get("steps").isArray() || tc.get("steps").isEmpty()) {
            errors.add(prefix + " (ui): 'steps' array is required and must not be empty");
            return;
        }
        JsonNode steps = tc.get("steps");
        for (int i = 0; i < steps.size(); i++) {
            JsonNode step = steps.get(i);
            String stepPrefix = prefix + ".steps[" + i + "]";
            validateRequiredString(step, "action", stepPrefix, errors);
            validateRequiredString(step, "target", stepPrefix, errors);

            if (step.has("action")) {
                String action = step.get("action").asText();
                if (!ALLOWED_ACTIONS.contains(action)) {
                    errors.add(stepPrefix + ": unknown action '" + action +
                               "'. Allowed: " + ALLOWED_ACTIONS);
                }
            }

            // Warn if a step target looks like a logical name not in PageRegistry
            if (step.has("target")) {
                String target = step.get("target").asText();
                if (looksLikeLogicalName(target) && !PageRegistry.allSelectors().containsKey(target)) {
                    log.warn("Validator: target '{}' in {} looks like a logical name " +
                             "but was not found in PageRegistry — add it to a Page Object class", target, stepPrefix);
                }
            }
        }
    }

    /** Returns true if a string looks like a logical name (word chars only, no CSS/URL prefix). */
    private boolean looksLikeLogicalName(String target) {
        if (target == null || target.isBlank()) return false;
        char first = target.charAt(0);
        return Character.isLetter(first) || first == '_';
    }

    private void validateApiTestCase(JsonNode tc, String prefix, List<String> errors) {
        if (!tc.has("request") || !tc.get("request").isObject()) {
            errors.add(prefix + " (api): 'request' object is required");
            return;
        }
        JsonNode req = tc.get("request");
        validateRequiredString(req, "method", prefix + ".request", errors);
        validateRequiredString(req, "url",    prefix + ".request", errors);

        if (req.has("method")) {
            String method = req.get("method").asText().toUpperCase();
            Set<String> validMethods = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
            if (!validMethods.contains(method)) {
                errors.add(prefix + ".request.method must be one of " + validMethods +
                           ", got: '" + method + "'");
            }
        }

        // Validate assertions if present
        if (tc.has("assertions") && tc.get("assertions").isArray()) {
            JsonNode assertions = tc.get("assertions");
            for (int i = 0; i < assertions.size(); i++) {
                JsonNode assertion = assertions.get(i);
                String aPrefix = prefix + ".assertions[" + i + "]";
                validateRequiredString(assertion, "type", aPrefix, errors);

                if (assertion.has("type")) {
                    String aType = assertion.get("type").asText();
                    if (!ALLOWED_ASSERTION_TYPES.contains(aType)) {
                        errors.add(aPrefix + ": unknown assertion type '" + aType +
                                   "'. Allowed: " + ALLOWED_ASSERTION_TYPES);
                    }
                }
            }
        }
    }

    private void validateRequiredString(JsonNode node, String field,
                                        String prefix, List<String> errors) {
        if (!node.has(field) || !node.get(field).isTextual() || node.get(field).asText().isBlank()) {
            errors.add(prefix + "." + field + " is required and must be a non-empty string");
        }
    }

    // -------------------------------------------------------------------------
    // RESULT TYPE
    // -------------------------------------------------------------------------

    public static class ValidationResult {
        private final boolean valid;
        private final TestSuite suite;
        private final String errorMessage;

        private ValidationResult(boolean valid, TestSuite suite, String errorMessage) {
            this.valid        = valid;
            this.suite        = suite;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success(TestSuite suite) {
            return new ValidationResult(true, suite, null);
        }

        public static ValidationResult failure(String error) {
            return new ValidationResult(false, null, error);
        }

        public boolean isValid()        { return valid;        }
        public TestSuite getSuite()     { return suite;        }
        public String getErrorMessage() { return errorMessage; }
    }
}
