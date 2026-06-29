package com.qa.ai.unit;

import com.qa.ai.validator.TestCaseValidator;
import com.qa.ai.validator.TestCaseValidator.ValidationResult;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Unit tests for TestCaseValidator — the hallucination guardrail layer.
 *
 * These tests verify that the validator correctly:
 *   - Accepts conforming JSON test cases
 *   - Rejects malformed JSON
 *   - Rejects disallowed action types (allow-list enforcement)
 *   - Rejects missing required fields
 *   - Rejects unknown test types
 *
 * The validator is the second of three hallucination mitigation layers.
 * If these tests break, the framework will accept invalid Claude output.
 */
public class TestCaseValidatorTest {

    private TestCaseValidator validator;

    @BeforeClass
    public void setUp() {
        validator = new TestCaseValidator();
    }

    // -------------------------------------------------------------------------
    // VALID JSON — should pass
    // -------------------------------------------------------------------------

    @Test(description = "Valid UI test case passes validation")
    public void validUiTestCase_passes() {
        String json = """
            {
              "tests": [{
                "id": "TC001",
                "title": "Valid login",
                "type": "ui",
                "tags": ["smoke"],
                "expected": "User is redirected to inventory page",
                "steps": [
                  { "action": "navigate",       "target": "https://example.com", "value": "" },
                  { "action": "fill",           "target": "#username",           "value": "user" },
                  { "action": "click",          "target": "#submit",             "value": "" },
                  { "action": "assert_visible", "target": ".dashboard",          "value": "" }
                ]
              }]
            }
            """;
        ValidationResult result = validator.validate(json);
        Assert.assertTrue(result.isValid(), "Expected valid UI test case to pass. Error: " + result.getErrorMessage());
        Assert.assertNotNull(result.getSuite());
        Assert.assertEquals(result.getSuite().totalCount(), 1);
    }

    @Test(description = "Valid API test case passes validation")
    public void validApiTestCase_passes() {
        String json = """
            {
              "tests": [{
                "id": "TC001",
                "title": "GET users returns 200",
                "type": "api",
                "tags": ["smoke", "users"],
                "expected": "API returns HTTP 200 with user data",
                "request": {
                  "method": "GET",
                  "url": "https://jsonplaceholder.typicode.com/users",
                  "params": {},
                  "headers": {},
                  "body": ""
                },
                "assertions": [
                  { "type": "status_code", "path": "", "expected": "200" },
                  { "type": "not_empty",   "path": "[0].name", "expected": "true" }
                ]
              }]
            }
            """;
        ValidationResult result = validator.validate(json);
        Assert.assertTrue(result.isValid(), "Expected valid API test case to pass. Error: " + result.getErrorMessage());
    }

    @Test(description = "Multiple test cases — UI and API mixed — all pass")
    public void mixedTestSuite_passes() {
        String json = """
            {
              "tests": [
                {
                  "id": "TC001", "title": "UI test", "type": "ui",
                  "tags": ["smoke"], "expected": "Page loads",
                  "steps": [
                    { "action": "navigate", "target": "https://example.com", "value": "" },
                    { "action": "assert_visible", "target": ".main", "value": "" }
                  ]
                },
                {
                  "id": "TC002", "title": "API test", "type": "api",
                  "tags": ["api"], "expected": "200 OK",
                  "request": { "method": "GET", "url": "https://api.example.com/health", "params": {}, "headers": {}, "body": "" },
                  "assertions": [{ "type": "status_code", "path": "", "expected": "200" }]
                }
              ]
            }
            """;
        ValidationResult result = validator.validate(json);
        Assert.assertTrue(result.isValid(), "Expected mixed suite to pass. Error: " + result.getErrorMessage());
        Assert.assertEquals(result.getSuite().totalCount(), 2);
    }

    // -------------------------------------------------------------------------
    // MALFORMED JSON — should fail
    // -------------------------------------------------------------------------

    @Test(description = "Malformed JSON fails validation with syntax error")
    public void malformedJson_fails() {
        String json = "{ this is not valid json }";
        ValidationResult result = validator.validate(json);
        Assert.assertFalse(result.isValid());
        Assert.assertNotNull(result.getErrorMessage());
        Assert.assertTrue(result.getErrorMessage().toLowerCase().contains("json") ||
                          result.getErrorMessage().toLowerCase().contains("syntax") ||
                          result.getErrorMessage().toLowerCase().contains("invalid"),
                "Error should mention JSON/syntax issue: " + result.getErrorMessage());
    }

    @Test(description = "Missing 'tests' array root fails validation")
    public void missingTestsArray_fails() {
        String json = "{ \"data\": [] }";
        ValidationResult result = validator.validate(json);
        Assert.assertFalse(result.isValid());
        Assert.assertTrue(result.getErrorMessage().contains("tests"),
                "Error should mention missing 'tests' key: " + result.getErrorMessage());
    }

    @Test(description = "Empty tests array fails validation")
    public void emptyTestsArray_fails() {
        String json = "{ \"tests\": [] }";
        ValidationResult result = validator.validate(json);
        Assert.assertFalse(result.isValid());
        Assert.assertTrue(result.getErrorMessage().toLowerCase().contains("empty") ||
                          result.getErrorMessage().toLowerCase().contains("least"),
                "Error should mention empty array: " + result.getErrorMessage());
    }

    // -------------------------------------------------------------------------
    // DISALLOWED ACTIONS — allow-list enforcement (hallucination guard)
    // -------------------------------------------------------------------------

    @Test(description = "Unknown UI action is rejected by allow-list")
    public void unknownAction_isRejected() {
        String json = """
            {
              "tests": [{
                "id": "TC001", "title": "Test", "type": "ui",
                "tags": ["smoke"], "expected": "Something happens",
                "steps": [
                  { "action": "navigate", "target": "https://example.com", "value": "" },
                  { "action": "execute_script", "target": "document.cookie='evil=1'", "value": "" }
                ]
              }]
            }
            """;
        ValidationResult result = validator.validate(json);
        Assert.assertFalse(result.isValid(), "execute_script should be rejected by allow-list");
        Assert.assertTrue(result.getErrorMessage().contains("execute_script") ||
                          result.getErrorMessage().toLowerCase().contains("unknown action"),
                "Error should identify the disallowed action: " + result.getErrorMessage());
    }

    @Test(description = "XSS-like payload in action field is rejected")
    public void xssPayloadInAction_isRejected() {
        String json = """
            {
              "tests": [{
                "id": "TC001", "title": "XSS test", "type": "ui",
                "tags": [], "expected": "test",
                "steps": [
                  { "action": "<script>alert('xss')</script>", "target": "#el", "value": "" }
                ]
              }]
            }
            """;
        ValidationResult result = validator.validate(json);
        Assert.assertFalse(result.isValid(), "XSS payload in action should be rejected");
    }

    @Test(description = "Unknown API assertion type is rejected by allow-list")
    public void unknownAssertionType_isRejected() {
        String json = """
            {
              "tests": [{
                "id": "TC001", "title": "API test", "type": "api",
                "tags": ["api"], "expected": "200",
                "request": { "method": "GET", "url": "https://api.example.com/", "params": {}, "headers": {}, "body": "" },
                "assertions": [
                  { "type": "sql_injection", "path": "'; DROP TABLE users;--", "expected": "" }
                ]
              }]
            }
            """;
        ValidationResult result = validator.validate(json);
        Assert.assertFalse(result.isValid(), "sql_injection assertion type should be rejected");
    }

    // -------------------------------------------------------------------------
    // MISSING REQUIRED FIELDS
    // -------------------------------------------------------------------------

    @Test(description = "Missing 'id' field fails validation")
    public void missingId_fails() {
        String json = """
            {
              "tests": [{
                "title": "Valid login", "type": "ui",
                "tags": ["smoke"], "expected": "Page loads",
                "steps": [{ "action": "navigate", "target": "https://example.com", "value": "" }]
              }]
            }
            """;
        ValidationResult result = validator.validate(json);
        Assert.assertFalse(result.isValid());
        Assert.assertTrue(result.getErrorMessage().contains("id"),
                "Error should mention missing 'id': " + result.getErrorMessage());
    }

    @Test(description = "Missing 'expected' field fails validation")
    public void missingExpected_fails() {
        String json = """
            {
              "tests": [{
                "id": "TC001", "title": "Test", "type": "ui",
                "tags": [],
                "steps": [{ "action": "navigate", "target": "https://example.com", "value": "" }]
              }]
            }
            """;
        ValidationResult result = validator.validate(json);
        Assert.assertFalse(result.isValid());
        Assert.assertTrue(result.getErrorMessage().contains("expected"),
                "Error should mention missing 'expected': " + result.getErrorMessage());
    }

    @Test(description = "Invalid type value fails validation")
    public void invalidType_fails() {
        String json = """
            {
              "tests": [{
                "id": "TC001", "title": "Test", "type": "mobile",
                "tags": [], "expected": "Something",
                "steps": [{ "action": "navigate", "target": "https://example.com", "value": "" }]
              }]
            }
            """;
        ValidationResult result = validator.validate(json);
        Assert.assertFalse(result.isValid());
        Assert.assertTrue(result.getErrorMessage().contains("mobile") ||
                          result.getErrorMessage().toLowerCase().contains("type"),
                "Error should mention the invalid type: " + result.getErrorMessage());
    }

    @Test(description = "UI test with empty steps fails validation")
    public void uiTestWithNoSteps_fails() {
        String json = """
            {
              "tests": [{
                "id": "TC001", "title": "Test", "type": "ui",
                "tags": [], "expected": "Something",
                "steps": []
              }]
            }
            """;
        ValidationResult result = validator.validate(json);
        Assert.assertFalse(result.isValid());
        Assert.assertTrue(result.getErrorMessage().toLowerCase().contains("steps"),
                "Error should mention steps: " + result.getErrorMessage());
    }

    @Test(description = "API test missing request object fails validation")
    public void apiTestWithNoRequest_fails() {
        String json = """
            {
              "tests": [{
                "id": "TC001", "title": "API test", "type": "api",
                "tags": [], "expected": "200 OK",
                "assertions": [{ "type": "status_code", "path": "", "expected": "200" }]
              }]
            }
            """;
        ValidationResult result = validator.validate(json);
        Assert.assertFalse(result.isValid());
        Assert.assertTrue(result.getErrorMessage().toLowerCase().contains("request"),
                "Error should mention missing request: " + result.getErrorMessage());
    }
}
