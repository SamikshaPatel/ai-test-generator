package com.qa.ai.config;

import com.qa.ai.reporter.AgentActivity;
import com.qa.ai.config.SensitiveDataMasker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves ${VAR} placeholders in test step targets and values
 * against entries in test-data.properties.
 *
 * Example: "${LOGIN_USER}" → "standard_user" (from test-data.properties)
 *
 * Unresolved variables are left as-is so tests fail loudly with the
 * original placeholder name rather than silently using a wrong value.
 */
public class TestDataResolver {

    private static final Logger log = LogManager.getLogger(TestDataResolver.class);
    private static final Pattern VAR = Pattern.compile("\\$\\{([^}]+)}");
    private static final TestDataResolver INSTANCE = new TestDataResolver();

    private final Properties data = new Properties();

    private TestDataResolver() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("test-data.properties")) {
            if (is != null) {
                data.load(is);
                log.info("Loaded test-data.properties ({} entries)", data.size());
            } else {
                log.debug("test-data.properties not found — variable substitution disabled");
            }
        } catch (IOException e) {
            log.warn("Could not load test-data.properties: {}", e.getMessage());
        }
    }

    public static TestDataResolver get() {
        return INSTANCE;
    }

    /**
     * Replaces all ${KEY} tokens in the input string with values from test-data.properties.
     * Returns the input unchanged if it contains no placeholders.
     */
    public String resolve(String input) {
        if (input == null || !input.contains("${")) return input;
        Matcher m = VAR.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);

            // Env var takes precedence — allows secrets to stay out of source files
            // Treat blank env vars (e.g. unset CI secrets passed as "") same as absent
            String value = System.getenv(key);
            if (value == null || value.isBlank()) {
                value = data.getProperty(key);
            }

            if (value == null) {
                log.warn("Test data variable '{}' not found in env or test-data.properties", key);
                value = m.group(0); // leave placeholder intact
            } else {
                // Record masked value for sensitive keys so credentials never appear in reports
                String logValue = SensitiveDataMasker.maskIfSensitive(key, value);
                AgentActivity.get().recordVariableResolved(key, logValue);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}