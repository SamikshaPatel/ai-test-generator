package com.qa.ai.claude;

import io.qameta.allure.Allure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Agentic test repair: when ALL fallback selectors fail, asks Claude to
 * suggest new selectors based on the live DOM snapshot.
 *
 * This is a HUMAN-IN-THE-LOOP repair loop:
 *   1. Captures DOM at the point of failure (already done by PlaywrightExecutor)
 *   2. Sends DOM + failed selector to Claude
 *   3. Claude suggests alternative selectors (ranked by confidence)
 *   4. Suggestions are attached to Allure as "AI Repair Suggestion"
 *   5. Saved to target/repair-suggestions/{testId}.json for CI triage
 *
 * Does NOT auto-apply suggestions — a QA engineer reviews and commits updates
 * to the Page Object or fallback_targets in the JSON story.
 *
 * REQUIRES: ANTHROPIC_API_KEY environment variable.
 * If not set, logs a warning and skips gracefully (repair is best-effort).
 */
public class SelfHealSuggester {

    private static final Logger log = LogManager.getLogger(SelfHealSuggester.class);

    private static final int MAX_DOM_CHARS = 6000; // truncate to fit Claude context window

    private SelfHealSuggester() {}

    /**
     * Asks Claude to suggest replacement selectors for a failed locator.
     *
     * @param testId          test case ID (e.g. "TC001")
     * @param failedSelector  the CSS selector that timed out on ALL candidates
     * @param action          the Playwright action that failed (click, fill, assert_visible, etc.)
     * @param domSnapshot     the full HTML DOM at the point of failure
     */
    public static void suggest(String testId, String failedSelector, String action, String domSnapshot) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("SelfHealSuggester: ANTHROPIC_API_KEY not set — skipping repair suggestion");
            return;
        }

        log.info("SelfHealSuggester: requesting repair suggestions for [{}] '{}' on '{}'",
                testId, action, failedSelector);

        try {
            ClaudeService claude = new ClaudeService();
            String truncatedDom = domSnapshot != null && domSnapshot.length() > MAX_DOM_CHARS
                    ? domSnapshot.substring(0, MAX_DOM_CHARS) + "\n... [DOM TRUNCATED]"
                    : domSnapshot;

            String repairPrompt = buildRepairPrompt(failedSelector, action, truncatedDom);
            claude.setSystemPrompt(buildRepairSystemPrompt());
            String suggestions = claude.generateTestCases(repairPrompt, "");

            // Attach to Allure
            String attachmentContent = buildSuggestionReport(testId, failedSelector, action, suggestions);
            Allure.addAttachment(
                "AI Repair Suggestion — " + testId,
                "text/plain",
                new ByteArrayInputStream(attachmentContent.getBytes(StandardCharsets.UTF_8)),
                ".txt"
            );

            // Save to target/repair-suggestions/ for CI triage
            saveSuggestionFile(testId, failedSelector, suggestions);

            log.info("SelfHealSuggester: repair suggestions attached for [{}]", testId);

        } catch (Exception e) {
            log.warn("SelfHealSuggester: could not generate repair suggestions — {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // PRIVATE HELPERS
    // -------------------------------------------------------------------------

    private static String buildRepairSystemPrompt() {
        return """
                You are a Playwright test repair specialist.

                When given a failed CSS selector and a DOM snapshot, you suggest alternative
                selectors that are likely to find the target element.

                OUTPUT RULES — strictly enforced:
                • Output ONLY a JSON array of suggested selectors, ranked by confidence (highest first).
                • Each item: { "selector": "...", "confidence": "high|medium|low", "reason": "..." }
                • Maximum 5 suggestions.
                • Do NOT include the failed selector itself.
                • Prefer data-test, id, and semantic attributes over fragile CSS classes.
                • Output ONLY the JSON array. No markdown, no preamble.

                Example output:
                [
                  { "selector": "[data-test='login-button']", "confidence": "high",   "reason": "stable data-test attribute" },
                  { "selector": "button[type='submit']",       "confidence": "medium", "reason": "semantic form button" }
                ]
                """;
    }

    private static String buildRepairPrompt(String failedSelector, String action, String dom) {
        return String.format("""
                FAILED SELECTOR: %s
                FAILED ACTION:   %s

                Find an element in the DOM below that matches this description.
                The selector '%s' no longer works — suggest alternatives.

                DOM SNAPSHOT:
                ---
                %s
                ---

                Output only the JSON array of suggestions.
                """, failedSelector, action, failedSelector, dom);
    }

    private static String buildSuggestionReport(String testId, String failedSelector,
                                                  String action, String suggestions) {
        return String.format(
            "╔══════════════════════════════════════════════════════╗\n" +
            "║          AI REPAIR SUGGESTION (Human-in-the-Loop)   ║\n" +
            "╚══════════════════════════════════════════════════════╝\n" +
            "Test ID         : %s\n" +
            "Failed Selector : %s\n" +
            "Failed Action   : %s\n\n" +
            "SUGGESTED ALTERNATIVES (review and apply manually):\n" +
            "%s\n\n" +
            "NEXT STEPS:\n" +
            "  1. Copy the best selector into the Page Object class fallbacks()\n" +
            "  2. OR add it as fallback_targets[] in the JSON test case\n" +
            "  3. Re-run to confirm self-healing activates\n" +
            "  NOTE: Do NOT auto-apply without manual review.",
            testId, failedSelector, action, suggestions
        );
    }

    private static void saveSuggestionFile(String testId, String failedSelector, String suggestions) {
        try {
            Path dir = Paths.get("target", "repair-suggestions");
            Files.createDirectories(dir);
            String content = String.format(
                "{\"testId\":\"%s\",\"failedSelector\":\"%s\",\"suggestions\":%s}",
                testId.replace("\"", "\\\""),
                failedSelector.replace("\"", "\\\""),
                suggestions
            );
            Files.writeString(dir.resolve(testId + ".json"), content, StandardCharsets.UTF_8);
            log.debug("Repair suggestion saved to: {}", dir.resolve(testId + ".json"));
        } catch (Exception e) {
            log.warn("Could not save repair suggestion file: {}", e.getMessage());
        }
    }
}
