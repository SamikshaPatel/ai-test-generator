package com.qa.ai.unit;

import com.qa.ai.config.SensitiveDataMasker;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Unit tests for SensitiveDataMasker — the compliance layer that prevents
 * secrets from appearing in Allure reports, HAR files, and logs.
 *
 * If these tests break, sensitive data (passwords, tokens, API keys) may
 * leak into test artifacts visible in CI dashboards or GitHub Pages reports.
 */
public class SensitiveDataMaskerTest {

    // -------------------------------------------------------------------------
    // KEY-BASED MASKING
    // -------------------------------------------------------------------------

    @Test(description = "Password key is detected as sensitive")
    public void passwordKey_isSensitive() {
        Assert.assertTrue(SensitiveDataMasker.isSensitiveKey("password"));
        Assert.assertTrue(SensitiveDataMasker.isSensitiveKey("PASSWORD"));
        Assert.assertTrue(SensitiveDataMasker.isSensitiveKey("userPassword"));
        Assert.assertTrue(SensitiveDataMasker.isSensitiveKey("login_pass"));
    }

    @Test(description = "Token key is detected as sensitive")
    public void tokenKey_isSensitive() {
        Assert.assertTrue(SensitiveDataMasker.isSensitiveKey("token"));
        Assert.assertTrue(SensitiveDataMasker.isSensitiveKey("access_token"));
        Assert.assertTrue(SensitiveDataMasker.isSensitiveKey("TOKEN"));
        Assert.assertTrue(SensitiveDataMasker.isSensitiveKey("authToken"));
    }

    @Test(description = "Authorization header key is detected as sensitive")
    public void authorizationKey_isSensitive() {
        Assert.assertTrue(SensitiveDataMasker.isSensitiveKey("Authorization"));
        Assert.assertTrue(SensitiveDataMasker.isSensitiveKey("authorization"));
        Assert.assertTrue(SensitiveDataMasker.isSensitiveKey("auth"));
        Assert.assertTrue(SensitiveDataMasker.isSensitiveKey("x-auth-token"));
    }

    @Test(description = "API key field is detected as sensitive")
    public void apiKeyField_isSensitive() {
        Assert.assertTrue(SensitiveDataMasker.isSensitiveKey("api_key"));
        Assert.assertTrue(SensitiveDataMasker.isSensitiveKey("apiKey"));
        Assert.assertTrue(SensitiveDataMasker.isSensitiveKey("API_KEY"));
        Assert.assertTrue(SensitiveDataMasker.isSensitiveKey("x-api-key"));
    }

    @Test(description = "Secret key is detected as sensitive")
    public void secretKey_isSensitive() {
        Assert.assertTrue(SensitiveDataMasker.isSensitiveKey("secret"));
        Assert.assertTrue(SensitiveDataMasker.isSensitiveKey("clientSecret"));
        Assert.assertTrue(SensitiveDataMasker.isSensitiveKey("SECRET_KEY"));
    }

    @Test(description = "Non-sensitive keys are not flagged")
    public void nonSensitiveKeys_areNotFlagged() {
        Assert.assertFalse(SensitiveDataMasker.isSensitiveKey("username"));
        Assert.assertFalse(SensitiveDataMasker.isSensitiveKey("email"));
        Assert.assertFalse(SensitiveDataMasker.isSensitiveKey("firstName"));
        Assert.assertFalse(SensitiveDataMasker.isSensitiveKey("userId"));
        Assert.assertFalse(SensitiveDataMasker.isSensitiveKey("Content-Type"));
        Assert.assertFalse(SensitiveDataMasker.isSensitiveKey("Accept"));
    }

    @Test(description = "Null key is not sensitive")
    public void nullKey_isNotSensitive() {
        Assert.assertFalse(SensitiveDataMasker.isSensitiveKey(null));
    }

    // -------------------------------------------------------------------------
    // maskIfSensitive — value masking
    // -------------------------------------------------------------------------

    @Test(description = "Sensitive key value is replaced with MASK")
    public void sensitiveKeyValue_isMasked() {
        String result = SensitiveDataMasker.maskIfSensitive("password", "secret_sauce");
        Assert.assertEquals(result, SensitiveDataMasker.MASK);
    }

    @Test(description = "Non-sensitive key value is returned unchanged")
    public void nonSensitiveKeyValue_isUnchanged() {
        String result = SensitiveDataMasker.maskIfSensitive("username", "standard_user");
        Assert.assertEquals(result, "standard_user");
    }

    // -------------------------------------------------------------------------
    // scrubJson — JSON body scrubbing
    // -------------------------------------------------------------------------

    @Test(description = "Password field in JSON body is scrubbed")
    public void jsonPasswordField_isScrubbed() {
        String json = "{\"username\": \"admin\", \"password\": \"super_secret\"}";
        String scrubbed = SensitiveDataMasker.scrubJson(json);
        Assert.assertFalse(scrubbed.contains("super_secret"),
                "Scrubbed JSON should not contain the password value");
        Assert.assertTrue(scrubbed.contains(SensitiveDataMasker.MASK),
                "Scrubbed JSON should contain MASK placeholder");
        Assert.assertTrue(scrubbed.contains("admin"),
                "Scrubbed JSON should preserve non-sensitive values");
    }

    @Test(description = "Token field in JSON is scrubbed")
    public void jsonTokenField_isScrubbed() {
        String json = "{\"access_token\": \"eyJhbGciOiJIUzI1NiJ9.payload.sig\", \"expires_in\": 3600}";
        String scrubbed = SensitiveDataMasker.scrubJson(json);
        Assert.assertFalse(scrubbed.contains("eyJhbGciOiJIUzI1NiJ9"),
                "Scrubbed JSON should not contain the token value");
    }

    @Test(description = "Nested JSON with secret fields is scrubbed")
    public void nestedJsonSecretFields_areScrubbed() {
        String json = "{\"user\":{\"name\":\"QA\",\"secret\":\"top_secret_value\"}}";
        String scrubbed = SensitiveDataMasker.scrubJson(json);
        Assert.assertFalse(scrubbed.contains("top_secret_value"),
                "Nested secret value should be scrubbed");
        Assert.assertTrue(scrubbed.contains("QA"),
                "Non-sensitive nested value should be preserved");
    }

    @Test(description = "Non-sensitive JSON is not modified")
    public void nonSensitiveJson_isUnchanged() {
        String json = "{\"userId\": 1, \"name\": \"John\", \"email\": \"john@example.com\"}";
        String scrubbed = SensitiveDataMasker.scrubJson(json);
        Assert.assertEquals(scrubbed, json, "Non-sensitive JSON should not be modified");
    }

    @Test(description = "Null JSON input returns null")
    public void nullJson_returnsNull() {
        Assert.assertNull(SensitiveDataMasker.scrubJson(null));
    }

    // -------------------------------------------------------------------------
    // scrubFormEncoded — form body scrubbing
    // -------------------------------------------------------------------------

    @Test(description = "Form-encoded password is scrubbed")
    public void formEncodedPassword_isScrubbed() {
        String body = "username=standard_user&password=secret_sauce&remember=true";
        String scrubbed = SensitiveDataMasker.scrubFormEncoded(body);
        Assert.assertFalse(scrubbed.contains("secret_sauce"),
                "Form-encoded password should be scrubbed");
        Assert.assertTrue(scrubbed.contains("standard_user"),
                "Non-sensitive form fields should be preserved");
        Assert.assertTrue(scrubbed.contains(SensitiveDataMasker.MASK));
    }

    @Test(description = "Form-encoded token is scrubbed")
    public void formEncodedToken_isScrubbed() {
        String body = "grant_type=password&token=abc123xyz&client_id=test";
        String scrubbed = SensitiveDataMasker.scrubFormEncoded(body);
        Assert.assertFalse(scrubbed.contains("abc123xyz"),
                "Form-encoded token should be scrubbed");
    }

    @Test(description = "Null form body returns null")
    public void nullFormBody_returnsNull() {
        Assert.assertNull(SensitiveDataMasker.scrubFormEncoded(null));
    }
}
