package com.qa.ai.reporter;

import com.qa.ai.config.ConfigManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Enforces minimum quality thresholds at the end of a test suite.
 *
 * A QA Director controls release gates — tests passing is necessary but not sufficient.
 * If the pass rate drops below threshold, CI must fail loudly.
 *
 * GATES (configurable via config.properties):
 *   quality.gate.min.pass.pct  — minimum % of tests that must pass (default: 80)
 *   quality.gate.max.flake.pct — maximum % of tests allowed to flake-retry (default: 20)
 *
 * On gate failure:
 *   1. Writes target/quality-gate-failure.txt (CI pipelines check for this file)
 *   2. Throws QualityGateException (fails the @AfterTest / @AfterSuite)
 *
 * On gate pass:
 *   - Removes any stale quality-gate-failure.txt from previous run
 *   - Logs GREEN banner
 */
public class QualityGateChecker {

    private static final Logger log = LogManager.getLogger(QualityGateChecker.class);

    private static final Path GATE_FAILURE_FILE = Paths.get("target", "quality-gate-failure.txt");

    private QualityGateChecker() {}

    /**
     * Evaluates quality gates for the completed suite.
     *
     * @param suiteName   module name (for logging)
     * @param passed      tests that passed
     * @param failed      tests that failed
     * @param skipped     tests that were skipped
     * @param retryCount  tests that required at least one retry (flake count)
     */
    public static void evaluate(String suiteName, int passed, int failed, int skipped, int retryCount) {
        int minPassPct  = ConfigManager.get().getInt("quality.gate.min.pass.pct",  80);
        int maxFlakePct = ConfigManager.get().getInt("quality.gate.max.flake.pct", 20);

        int total = passed + failed + skipped;
        if (total == 0) {
            log.warn("QualityGate [{}]: no tests ran — skipping gate evaluation", suiteName);
            return;
        }

        int passPct  = (int)(100.0 * passed / total);
        int flakePct = retryCount > 0 ? (int)(100.0 * retryCount / total) : 0;

        boolean passGateOk  = passPct  >= minPassPct;
        boolean flakeGateOk = flakePct <= maxFlakePct;

        if (passGateOk && flakeGateOk) {
            log.info("QualityGate [{}]: PASS — {}% passed (min {}%) | {}% flaked (max {}%)",
                    suiteName, passPct, minPassPct, flakePct, maxFlakePct);
            deleteStaleFailureFile();
            printGateBanner(suiteName, passPct, flakePct, minPassPct, maxFlakePct, true);
        } else {
            StringBuilder reason = new StringBuilder();
            reason.append("Quality gate FAILED for suite: ").append(suiteName).append("\n");
            if (!passGateOk) {
                reason.append(String.format("  ✘ Pass rate %d%% is below minimum threshold %d%%%n",
                        passPct, minPassPct));
            }
            if (!flakeGateOk) {
                reason.append(String.format("  ✘ Flake rate %d%% exceeds maximum threshold %d%%%n",
                        flakePct, maxFlakePct));
            }
            reason.append(String.format("  Tests: %d passed, %d failed, %d skipped, %d retried%n",
                    passed, failed, skipped, retryCount));

            writeFailureFile(reason.toString());
            printGateBanner(suiteName, passPct, flakePct, minPassPct, maxFlakePct, false);
            throw new QualityGateException(reason.toString());
        }
    }

    // -------------------------------------------------------------------------
    // PRIVATE HELPERS
    // -------------------------------------------------------------------------

    private static void writeFailureFile(String reason) {
        try {
            Files.createDirectories(GATE_FAILURE_FILE.getParent());
            Files.writeString(GATE_FAILURE_FILE, reason, StandardCharsets.UTF_8);
            log.error("Quality gate failure written to: {}", GATE_FAILURE_FILE.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Could not write quality gate failure file: {}", e.getMessage());
        }
    }

    private static void deleteStaleFailureFile() {
        try {
            Files.deleteIfExists(GATE_FAILURE_FILE);
        } catch (IOException e) {
            log.warn("Could not delete stale gate failure file: {}", e.getMessage());
        }
    }

    private static void printGateBanner(String suiteName, int passPct, int flakePct,
                                         int minPassPct, int maxFlakePct, boolean passed) {
        String status = passed ? "PASSED" : "FAILED";
        String color  = passed ? "✔" : "✘";
        String sep    = "═".repeat(52);
        System.out.printf("%n╔%s╗%n", sep);
        System.out.printf("║  QUALITY GATE %-38s║%n", status + " — " + suiteName);
        System.out.printf("╠%s╣%n", sep);
        System.out.printf("║  %s Pass Rate  : %3d%%  (threshold: ≥%d%%)%24s║%n",
                color, passPct, minPassPct, "");
        System.out.printf("║  %s Flake Rate : %3d%%  (threshold: ≤%d%%)%23s║%n",
                passed ? "✔" : "✘", flakePct, maxFlakePct, "");
        System.out.printf("╚%s╝%n%n", sep);
    }

    // -------------------------------------------------------------------------
    // EXCEPTION
    // -------------------------------------------------------------------------

    public static class QualityGateException extends RuntimeException {
        public QualityGateException(String message) { super(message); }
    }
}
