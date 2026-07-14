package com.qa.ai.retry;

import com.qa.ai.config.ConfigManager;
import com.qa.ai.reporter.AgentActivity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * Retries tests that fail with a Playwright TimeoutError (flaky timing issues).
 * Hard assertion failures (wrong text, wrong URL) are NOT retried — only timeouts.
 *
 * Max retries is controlled by retry.max in config.properties (default: 2).
 *
 * Usage: add retryAnalyzer = FlakeRetryAnalyzer.class to @Test annotations,
 *        or wire globally via a TestNG listener in testng.xml.
 */
public class FlakeRetryAnalyzer implements IRetryAnalyzer {

    private static final Logger log = LogManager.getLogger(FlakeRetryAnalyzer.class);
    private static final int MAX_RETRIES = ConfigManager.get().getInt("retry.max", 2);

    private int retryCount = 0;

    @Override
    public boolean retry(ITestResult result) {
        if (retryCount >= MAX_RETRIES) return false;

        Throwable cause = result.getThrowable();
        if (cause == null) return false;

        // Only retry on Playwright TimeoutError — not on assertion failures
        boolean isTimeout = isTimeoutError(cause);
        if (isTimeout) {
            retryCount++;
            AgentActivity.get().recordFlakeRetry();
            log.warn("Flaky test — retrying ({}/{}) '{}': {}",
                    retryCount, MAX_RETRIES,
                    result.getName(),
                    cause.getMessage());
            return true;
        }

        return false;
    }

    private boolean isTimeoutError(Throwable t) {
        String msg = t.getMessage();
        if (msg == null) return false;
        // Playwright TimeoutError messages contain "Timeout" or "timeout"
        if (msg.contains("TimeoutError") || msg.contains("Timeout") || msg.contains("timeout exceeded")) {
            return true;
        }
        // Check cause chain
        if (t.getCause() != null && t.getCause() != t) {
            return isTimeoutError(t.getCause());
        }
        return false;
    }
}