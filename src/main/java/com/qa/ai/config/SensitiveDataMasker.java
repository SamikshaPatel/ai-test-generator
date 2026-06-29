package com.qa.ai.config;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Central masking utility for sensitive data in logs, Allure attachments,
 * and HAR/network captures.
 *
 * Any key whose lower-cased name contains one of SENSITIVE_SUBSTRINGS is
 * considered sensitive.  Use {@link #maskIfSensitive} at every point where
 * a resolved value would be written to a report or log.
 */
public class SensitiveDataMasker {

    public static final String MASK = "[MASKED]";

    /** Substrings that flag a key as sensitive (case-insensitive). */
    private static final Set<String> SENSITIVE_SUBSTRINGS = Set.of(
            "pass", "password", "passwd", "secret", "token",
            "apikey", "api_key", "api-key", "authorization", "auth"
    );

    /**
     * JSON string values whose key matches a sensitive pattern:
     *   "password": "abc123"  →  "password": "[MASKED]"
     */
    private static final Pattern JSON_SENSITIVE = Pattern.compile(
            "(?i)(\"(?:password|passwd|secret|token|api[_\\-]?key|auth(?:orization)?)[^\"]*\"\\s*:\\s*\")([^\"]+)(\")"
    );

    /**
     * Form-encoded sensitive fields:
     *   password=abc123&  →  password=[MASKED]&
     */
    private static final Pattern FORM_SENSITIVE = Pattern.compile(
            "(?i)((?:password|passwd|secret|token|api[_\\-]?key)=)([^&\\s]+)"
    );

    private SensitiveDataMasker() {}

    // -------------------------------------------------------------------------
    // KEY-BASED MASKING
    // -------------------------------------------------------------------------

    public static boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        return SENSITIVE_SUBSTRINGS.stream().anyMatch(lower::contains);
    }

    /** Returns MASK if the key is sensitive; otherwise returns value unchanged. */
    public static String maskIfSensitive(String key, String value) {
        return isSensitiveKey(key) ? MASK : value;
    }

    // -------------------------------------------------------------------------
    // STRUCTURED CONTENT SCRUBBING
    // -------------------------------------------------------------------------

    /**
     * Replaces sensitive values in a JSON string.
     * Safe to call on HAR files, API request bodies, etc.
     */
    public static String scrubJson(String json) {
        if (json == null) return null;
        return JSON_SENSITIVE.matcher(json).replaceAll("$1" + MASK + "$3");
    }

    /**
     * Replaces sensitive values in a URL-form-encoded body (e.g. login form POST).
     */
    public static String scrubFormEncoded(String body) {
        if (body == null) return null;
        return FORM_SENSITIVE.matcher(body).replaceAll("$1" + MASK);
    }
}