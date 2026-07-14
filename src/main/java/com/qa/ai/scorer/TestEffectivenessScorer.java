package com.qa.ai.scorer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Scores how effectively a test suite performed at runtime — after tests execute.
 *
 * Complements {@link AITestOutput_QualityScorer}, which scores Claude's JSON output
 * at generation time. This scorer answers the question the output scorer cannot:
 * "Did these tests actually work when run against the real application?"
 *
 * Runs in @AfterTest inside BaseTest, once all tests in a suite have completed.
 *
 * SCORING DIMENSIONS (each 0–25, total 0–100):
 *
 *   1. Pass Rate          — percentage of tests that passed (core signal)
 *   2. Selector Stability — how often self-healing was needed (fewer = more stable)
 *   3. Flake Resistance   — how often timeout retries fired (fewer = more reliable)
 *   4. Selector Coverage  — unknown targets (not in PageRegistry) indicate fragile steps
 */
public class TestEffectivenessScorer {

    private static final Logger log = LogManager.getLogger(TestEffectivenessScorer.class);

    private TestEffectivenessScorer() {}

    /**
     * Scores the runtime effectiveness of a completed test suite.
     *
     * @param passed          tests that passed
     * @param failed          tests that failed
     * @param skipped         tests that were skipped
     * @param selfHeals       total self-heal events during the suite
     * @param flakeRetries    total timeout retries fired during the suite
     * @param unknownTargets  step targets not found in PageRegistry
     * @return {@link EffectivenessScore} with per-dimension breakdown
     */
    public static EffectivenessScore score(int passed, int failed, int skipped,
                                           int selfHeals, int flakeRetries, int unknownTargets) {
        int total = passed + failed + skipped;
        if (total == 0) {
            log.warn("TestEffectivenessScorer: no tests ran — returning zero score");
            return new EffectivenessScore(0, 0, 0, 0, 0);
        }

        int passRateScore     = scorePassRate(passed, total);
        int stabilityScore    = scoreSelectorStability(selfHeals, total);
        int flakeScore        = scoreFlakeResistance(flakeRetries, total);
        int coverageScore     = scoreSelectorCoverage(unknownTargets);

        int totalScore = passRateScore + stabilityScore + flakeScore + coverageScore;

        log.info("TestEffectivenessScorer: passRate={} stability={} flakeResistance={} " +
                 "selectorCoverage={} TOTAL={}",
                 passRateScore, stabilityScore, flakeScore, coverageScore, totalScore);

        return new EffectivenessScore(totalScore, passRateScore, stabilityScore, flakeScore, coverageScore);
    }

    // -------------------------------------------------------------------------
    // DIMENSION SCORERS
    // -------------------------------------------------------------------------

    /**
     * DIMENSION 1 — Pass Rate (0–25).
     * Direct percentage of tests that passed, scaled to 25.
     * 100% pass = 25, 80% = 20, 50% = 12, 0% = 0.
     */
    private static int scorePassRate(int passed, int total) {
        int score = (int)(25.0 * passed / total);
        log.debug("passRate score: {}/25 (passed={}/{})", score, passed, total);
        return score;
    }

    /**
     * DIMENSION 2 — Selector Stability (0–25).
     * Penalises suites where many steps needed self-healing.
     * 0 heals = 25 (perfect), 1+ heals per test = scaled down, floored at 0.
     *
     * Rationale: self-heals mean selectors drifted from what was generated —
     * a sign the test suite needs PageObject or fallback_targets updates.
     */
    private static int scoreSelectorStability(int selfHeals, int total) {
        double healsPerTest = (double) selfHeals / total;
        // Each heal-per-test deducts 25 points; more than 1 heal per test = 0
        int score = Math.max(0, 25 - (int)(healsPerTest * 25));
        log.debug("selectorStability score: {}/25 (selfHeals={}, healsPerTest={})",
                score, selfHeals, String.format("%.2f", healsPerTest));
        return score;
    }

    /**
     * DIMENSION 3 — Flake Resistance (0–25).
     * Penalises suites where tests needed timeout retries to pass.
     * 0 retries = 25 (rock solid), scaled down by retry rate, floored at 0.
     *
     * Rationale: retries mask timing instability — tests that need retries
     * are not reliably detecting real failures.
     */
    private static int scoreFlakeResistance(int flakeRetries, int total) {
        double retriesPerTest = (double) flakeRetries / total;
        int score = Math.max(0, 25 - (int)(retriesPerTest * 25));
        log.debug("flakeResistance score: {}/25 (flakeRetries={}, retriesPerTest={})",
                score, flakeRetries, String.format("%.2f", retriesPerTest));
        return score;
    }

    /**
     * DIMENSION 4 — Selector Coverage (0–25).
     * Penalises suites with step targets that weren't found in PageRegistry.
     * Unknown targets = steps using raw CSS or logical names not registered —
     * these can't benefit from self-healing fallback chains.
     *
     * 0 unknowns = 25, each unknown deducts 5 points, floored at 0.
     */
    private static int scoreSelectorCoverage(int unknownTargets) {
        int score = Math.max(0, 25 - (unknownTargets * 5));
        log.debug("selectorCoverage score: {}/25 (unknownTargets={})", score, unknownTargets);
        return score;
    }

    // -------------------------------------------------------------------------
    // RESULT RECORD
    // -------------------------------------------------------------------------

    public record EffectivenessScore(
        int total,
        int passRate,
        int selectorStability,
        int flakeResistance,
        int selectorCoverage
    ) {
        public String tier() {
            if (total >= 90) return "EXCELLENT";
            if (total >= 70) return "GOOD";
            if (total >= 50) return "FAIR";
            if (total >= 25) return "POOR";
            return "CRITICAL";
        }

        public String toReport() {
            return String.format(
                "╔══════════════════════════════════════╗\n" +
                "║  TEST EFFECTIVENESS SCORE: %3d/100   ║\n" +
                "╠══════════════════════════════════════╣\n" +
                "║  Pass Rate          : %3d/25         ║\n" +
                "║  Selector Stability : %3d/25         ║\n" +
                "║  Flake Resistance   : %3d/25         ║\n" +
                "║  Selector Coverage  : %3d/25         ║\n" +
                "║  Tier               : %-14s║\n" +
                "╚══════════════════════════════════════╝",
                total,
                passRate, selectorStability, flakeResistance, selectorCoverage,
                tier());
        }
    }
}
