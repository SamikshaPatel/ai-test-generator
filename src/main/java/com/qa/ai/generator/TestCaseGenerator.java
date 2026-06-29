package com.qa.ai.generator;

import com.qa.ai.model.TestSuite;
import com.qa.ai.pages.PageRegistry;
import com.qa.ai.reporter.AgentActivity;
import com.qa.ai.scorer.TestQualityScorer;
import com.qa.ai.validator.TestCaseValidator;
import com.qa.ai.validator.TestCaseValidator.ValidationResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * Orchestrates test case generation. Supports TWO modes:
 *
 *   MODE 1 — FILE MODE  (no API key needed — works with claude.ai subscription)
 *     Load pre-generated JSON saved from a claude.ai chat session.
 *     Use: generateFromJson("src/main/resources/stories/generated/my-tests.json")
 *
 *   MODE 2 — API MODE  (requires ANTHROPIC_API_KEY env var + API credits)
 *     Calls Claude API live with retry logic.
 *     Use: generateFromFile(storyPath, baseUrl)
 *
 * HOW TO USE FILE MODE (with your claude.ai subscription):
 *   1. Open claude.ai → paste the system prompt from PromptTemplates.systemPrompt()
 *      followed by your user story.
 *   2. Copy Claude's JSON response.
 *   3. Save it to src/main/resources/stories/generated/my-tests.json
 *   4. Call generateFromJson() — no API key required.
 *
 * Both modes use the same validator and produce the same TestSuite.
 * Playwright + RestAssured + Allure execute identically in both modes.
 */
public class TestCaseGenerator {

    private static final Logger log = LogManager.getLogger(TestCaseGenerator.class);

    private final TestCaseValidator validator = new TestCaseValidator();

    // =========================================================================
    // MODE 1 — FILE MODE (your claude.ai subscription, no API credits)
    // =========================================================================

    /**
     * Load test cases from a pre-generated JSON file produced via claude.ai chat.
     *
     * @param jsonFilePath path to JSON file, e.g.
     *                     "src/main/resources/stories/generated/user-api-tests.json"
     */
    public TestSuite generateFromJson(String jsonFilePath) {
        log.info("=== FILE MODE: Loading pre-generated JSON from {} ===", jsonFilePath);

        String rawJson = readFile(jsonFilePath);
        ValidationResult result = validator.validate(rawJson);

        if (!result.isValid()) {
            throw new TestGenerationException(
                    "JSON file failed validation: " + result.getErrorMessage() +
                            "\nFix the JSON or regenerate via claude.ai.\nFile: " + jsonFilePath);
        }

        TestSuite suite = result.getSuite();
        suite.setStorySource("file:" + jsonFilePath);
        suite.setModelUsed("claude.ai subscription (file mode)");
        suite.setGeneratedAt(Instant.now().toString());
        suite.setRetryCount(0);
        suite.setRawClaudeJson(rawJson);

        // Score AI output quality and record for trend tracking
        TestQualityScorer.QualityScore quality = TestQualityScorer.score(suite.getTests());
        AgentActivity.get().recordQualityScore(quality);
        log.info("AI Quality Score: {}/100 ({})", quality.total(), quality.tier());

        log.info("=== File Mode Complete: {} ===", suite);
        return suite;
    }

    // =========================================================================
    // MODE 2 — API MODE (requires ANTHROPIC_API_KEY)
    // =========================================================================

    /**
     * Generate test cases live via Claude API from a user story file.
     * Requires ANTHROPIC_API_KEY environment variable and API credits.
     */
    public TestSuite generateFromFile(String storyFilePath, String baseUrl) {
        log.info("=== API MODE: Generating via Claude API ===");
        requireApiKey();
        return generateViaApi(readFile(storyFilePath), baseUrl, storyFilePath);
    }

    /** Generate from an inline story string via Claude API. */
    public TestSuite generateFromStory(String userStory, String baseUrl) {
        log.info("=== API MODE: Generating from inline story ===");
        requireApiKey();
        return generateViaApi(userStory, baseUrl, "inline");
    }

    // =========================================================================
    // PRIVATE — API PIPELINE
    // =========================================================================

    private TestSuite generateViaApi(String story, String baseUrl, String source) {
        // ClaudeService is loaded lazily so file mode never fails on missing API key
        com.qa.ai.claude.ClaudeService claude = new com.qa.ai.claude.ClaudeService();
        int retryCount = 0;

        // Inject known PageRegistry logical names into the system prompt
        // so Claude reuses them instead of inventing raw CSS selectors
        claude.setSystemPrompt(com.qa.ai.claude.PromptTemplates.systemPrompt(
                PageRegistry.allSelectors().keySet()));

        log.info("Attempt 1: calling Claude API with {} known locators...",
                PageRegistry.allSelectors().size());
        String rawJson = claude.generateTestCases(story, baseUrl);
        ValidationResult result = validator.validate(rawJson);

        if (!result.isValid()) {
            log.warn("Attempt 1 failed: {}. Retrying with error context...", result.getErrorMessage());
            rawJson = claude.retryWithError(story, baseUrl, result.getErrorMessage());
            result  = validator.validate(rawJson);
            retryCount = 1;

            if (!result.isValid()) {
                throw new TestGenerationException(
                        "Claude produced invalid JSON after 2 attempts.\n" +
                                "Last error: " + result.getErrorMessage() + "\n" +
                                "Last response:\n" + rawJson);
            }
        }

        TestSuite suite = result.getSuite();
        suite.setStorySource(source);
        suite.setModelUsed("claude-sonnet-4-6 (API mode)");
        suite.setGeneratedAt(Instant.now().toString());
        suite.setRetryCount(retryCount);
        suite.setRawClaudeJson(rawJson);

        // Score AI output quality and record for trend tracking
        TestQualityScorer.QualityScore quality = TestQualityScorer.score(suite.getTests());
        AgentActivity.get().recordQualityScore(quality);
        log.info("AI Quality Score: {}/100 ({}) — retryCount={}", quality.total(), quality.tier(), retryCount);

        log.info("=== API Mode Complete: {} ===", suite);
        return suite;
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    private void requireApiKey() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        if (key == null || key.isBlank()) {
            throw new TestGenerationException(
                    "ANTHROPIC_API_KEY not set. Switch to file mode:\n" +
                            "  generator.generateFromJson(\"src/main/resources/stories/generated/user-api-tests.json\")");
        }
    }

    private String readFile(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)));
        } catch (IOException e) {
            throw new TestGenerationException("Cannot read file: " + path + " — " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // EXCEPTION
    // =========================================================================

    public static class TestGenerationException extends RuntimeException {
        public TestGenerationException(String message)                  { super(message); }
        public TestGenerationException(String message, Throwable cause) { super(message, cause); }
    }
}
