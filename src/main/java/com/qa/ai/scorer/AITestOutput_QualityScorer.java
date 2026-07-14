package com.qa.ai.scorer;

import com.qa.ai.model.ApiAssertion;
import com.qa.ai.model.TestCase;
import com.qa.ai.model.TestStep;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Scores AI-generated test suites for output quality — not just syntactic validity.
 *
 * Runs at GENERATION TIME (before any test executes) on the structure of
 * Claude's JSON output. Answers: "Did Claude produce well-structured tests?"
 *
 * For post-execution runtime effectiveness, see {@link TestEffectivenessScorer}.
 *
 * SCORING DIMENSIONS (each 0–25, total 0–100):
 *
 *   1. Assertion Depth    — how many distinct assertion types are used per test
 *   2. Negative Coverage  — at least 1 test tagged "negative" or "invalid"
 *   3. Edge Case Coverage — at least 1 test tagged "edge" or "edge-case"
 *   4. Step Realism       — steps in expected range: 3–12 (< 3 = trivial, > 12 = hallucination risk)
 *
 * Score is attached to Allure and persisted in runs.json for trend tracking.
 */
public class AITestOutput_QualityScorer {

    private static final Logger log = LogManager.getLogger(AITestOutput_QualityScorer.class);

    // Thresholds
    private static final int MIN_REALISTIC_STEPS = 3;
    private static final int MAX_REALISTIC_STEPS = 12;

    private AITestOutput_QualityScorer() {}

    /**
     * Scores the full list of generated test cases and returns a 0–100 quality score.
     *
     * @param tests the AI-generated test cases to evaluate
     * @return {@link QualityScore} with the aggregate score and per-dimension breakdown
     */
    public static QualityScore score(List<TestCase> tests) {
        if (tests == null || tests.isEmpty()) {
            log.warn("AITestOutput_QualityScorer: no tests to score");
            return new QualityScore(0, 0, 0, 0, 0, "No tests provided");
        }

        int assertionDepth    = scoreAssertionDepth(tests);
        int negativeCoverage  = scoreNegativeCoverage(tests);
        int edgeCaseCoverage  = scoreEdgeCaseCoverage(tests);
        int stepRealism       = scoreStepRealism(tests);

        int total = assertionDepth + negativeCoverage + edgeCaseCoverage + stepRealism;

        log.info("AITestOutput_QualityScorer: assertionDepth={} negativeCoverage={} " +
                 "edgeCaseCoverage={} stepRealism={} TOTAL={}",
                 assertionDepth, negativeCoverage, edgeCaseCoverage, stepRealism, total);

        return new QualityScore(total, assertionDepth, negativeCoverage,
                                edgeCaseCoverage, stepRealism, buildSummary(tests));
    }

    // -------------------------------------------------------------------------
    // DIMENSION SCORERS
    // -------------------------------------------------------------------------

    /**
     * DIMENSION 1 — Assertion Depth (0–25).
     *
     * Measures how many distinct assertion/action types are used across all tests.
     * Deep tests use assert_text + assert_visible + assert_url_contains, not just assert_url_contains.
     *
     * Score:
     *   - UI: avg assertions-per-test using distinct types (check assert_* steps)
     *   - API: avg assertion types used per test
     */
    private static int scoreAssertionDepth(List<TestCase> tests) {
        if (tests.isEmpty()) return 0;

        double totalDepth = 0;
        for (TestCase tc : tests) {
            if ("ui".equals(tc.getType())) {
                long assertionSteps = 0;
                if (tc.getSteps() != null) {
                    assertionSteps = tc.getSteps().stream()
                        .filter(s -> s.getAction() != null && s.getAction().startsWith("assert_"))
                        .count();
                }
                // 0 assertions = 0, 1 = shallow, 2 = ok, 3+ = deep
                totalDepth += Math.min(assertionSteps, 3) / 3.0;
            } else {
                long assertionCount = 0;
                if (tc.getAssertions() != null) {
                    assertionCount = tc.getAssertions().size();
                }
                totalDepth += Math.min(assertionCount, 3) / 3.0;
            }
        }

        double avgDepth = totalDepth / tests.size();
        int score = (int)(avgDepth * 25);
        log.debug("assertionDepth score: {}/25 (avgDepth={})", score, String.format("%.2f", avgDepth));
        return score;
    }

    /**
     * DIMENSION 2 — Negative Coverage (0–25).
     *
     * At least one test must have "negative" or "invalid" in its tags or title.
     * A suite with no negative tests can't catch rejection/error-handling regressions.
     *
     * Score: 25 if coverage exists, scaled by ratio of negative tests (min 25 if at least 1).
     */
    private static int scoreNegativeCoverage(List<TestCase> tests) {
        long negativeCount = tests.stream()
            .filter(tc -> {
                boolean tagMatch = tc.getTags() != null && tc.getTags().stream()
                    .anyMatch(t -> t.toLowerCase().contains("negative") || t.toLowerCase().contains("invalid"));
                boolean titleMatch = tc.getTitle() != null &&
                    (tc.getTitle().toLowerCase().contains("negative") ||
                     tc.getTitle().toLowerCase().contains("invalid") ||
                     tc.getTitle().toLowerCase().contains("wrong") ||
                     tc.getTitle().toLowerCase().contains("locked") ||
                     tc.getTitle().toLowerCase().contains("error") ||
                     tc.getTitle().toLowerCase().contains("fail") ||
                     tc.getTitle().toLowerCase().contains("missing") ||
                     tc.getTitle().toLowerCase().contains("empty"));
                return tagMatch || titleMatch;
            })
            .count();

        int score = negativeCount > 0 ? 25 : 0;
        log.debug("negativeCoverage score: {}/25 (negativeTests={})", score, negativeCount);
        return score;
    }

    /**
     * DIMENSION 3 — Edge Case Coverage (0–25).
     *
     * At least one test must have "edge" in its tags or cover boundary conditions.
     */
    private static int scoreEdgeCaseCoverage(List<TestCase> tests) {
        long edgeCount = tests.stream()
            .filter(tc -> {
                boolean tagMatch = tc.getTags() != null && tc.getTags().stream()
                    .anyMatch(t -> t.toLowerCase().contains("edge") ||
                                  t.toLowerCase().contains("boundary") ||
                                  t.toLowerCase().contains("corner"));
                boolean titleMatch = tc.getTitle() != null &&
                    (tc.getTitle().toLowerCase().contains("edge") ||
                     tc.getTitle().toLowerCase().contains("boundary") ||
                     tc.getTitle().toLowerCase().contains("empty") ||
                     tc.getTitle().toLowerCase().contains("null") ||
                     tc.getTitle().toLowerCase().contains("limit") ||
                     tc.getTitle().toLowerCase().contains("max") ||
                     tc.getTitle().toLowerCase().contains("min") ||
                     tc.getTitle().toLowerCase().contains("non-existent") ||
                     tc.getTitle().toLowerCase().contains("not found"));
                return tagMatch || titleMatch;
            })
            .count();

        int score = edgeCount > 0 ? 25 : 0;
        log.debug("edgeCaseCoverage score: {}/25 (edgeTests={})", score, edgeCount);
        return score;
    }

    /**
     * DIMENSION 4 — Step Realism (0–25).
     *
     * Test steps should be in a realistic range: 3–12.
     *   < 3 steps = trivial test (probably just navigate + assert)
     *   > 12 steps = hallucination risk or test doing too much (should be split)
     *
     * Score: percentage of tests with realistic step counts × 25.
     */
    private static int scoreStepRealism(List<TestCase> tests) {
        if (tests.isEmpty()) return 0;

        long realisticCount = tests.stream()
            .filter(tc -> {
                int stepCount = 0;
                if ("ui".equals(tc.getType()) && tc.getSteps() != null) {
                    stepCount = tc.getSteps().size();
                } else if ("api".equals(tc.getType()) && tc.getAssertions() != null) {
                    // API tests: 1 request + N assertions; realistic = 1–5 assertions
                    stepCount = 1 + tc.getAssertions().size();
                    return stepCount >= MIN_REALISTIC_STEPS && stepCount <= MAX_REALISTIC_STEPS;
                }
                return stepCount >= MIN_REALISTIC_STEPS && stepCount <= MAX_REALISTIC_STEPS;
            })
            .count();

        double ratio = (double) realisticCount / tests.size();
        int score = (int)(ratio * 25);
        log.debug("stepRealism score: {}/25 (realistic={}/{})", score, realisticCount, tests.size());
        return score;
    }

    // -------------------------------------------------------------------------
    // HUMAN-READABLE SUMMARY
    // -------------------------------------------------------------------------

    private static String buildSummary(List<TestCase> tests) {
        long uiCount  = tests.stream().filter(tc -> "ui".equals(tc.getType())).count();
        long apiCount = tests.stream().filter(tc -> "api".equals(tc.getType())).count();

        double avgUiSteps = tests.stream()
            .filter(tc -> "ui".equals(tc.getType()) && tc.getSteps() != null)
            .mapToInt(tc -> tc.getSteps().size())
            .average().orElse(0);

        double avgApiAssertions = tests.stream()
            .filter(tc -> "api".equals(tc.getType()) && tc.getAssertions() != null)
            .mapToInt(tc -> tc.getAssertions().size())
            .average().orElse(0);

        return String.format(
            "Tests: %d (%d UI / %d API) | Avg UI steps: %.1f | Avg API assertions: %.1f",
            tests.size(), uiCount, apiCount, avgUiSteps, avgApiAssertions);
    }

    // -------------------------------------------------------------------------
    // RESULT RECORD
    // -------------------------------------------------------------------------

    public record QualityScore(
        int total,
        int assertionDepth,
        int negativeCoverage,
        int edgeCaseCoverage,
        int stepRealism,
        String summary
    ) {
        /** Quality tier based on total score. */
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
                "║  AI OUTPUT QUALITY SCORE: %3d/100    ║\n" +
                "╠══════════════════════════════════════╣\n" +
                "║  Assertion Depth    : %3d/25         ║\n" +
                "║  Negative Coverage  : %3d/25         ║\n" +
                "║  Edge Case Coverage : %3d/25         ║\n" +
                "║  Step Realism       : %3d/25         ║\n" +
                "║  Tier               : %-14s║\n" +
                "╠══════════════════════════════════════╣\n" +
                "║  %-38s║\n" +
                "╚══════════════════════════════════════╝",
                total,
                assertionDepth, negativeCoverage, edgeCaseCoverage, stepRealism,
                tier(),
                summary.length() > 38 ? summary.substring(0, 35) + "..." : summary
            );
        }
    }
}
