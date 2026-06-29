package com.qa.ai.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Wrapper around the Anthropic Java SDK.
 *
 * Responsibilities:
 *   • Build and send prompts to Claude
 *   • Extract raw text from the response
 *   • Log every request/response for traceability (visible in Allure via Log4j)
 *
 * Authentication: reads ANTHROPIC_API_KEY environment variable automatically.
 * Set it in IntelliJ via: Run → Edit Configurations → Environment Variables.
 */
public class ClaudeService {

    private static final Logger log = LogManager.getLogger(ClaudeService.class);

    // Model to use — claude-sonnet-4-5 confirmed in SDK 2.30.0.
    // Update to CLAUDE_SONNET_4_6 once enum is available, or bump model ID below.
    private static final Model MODEL = Model.CLAUDE_SONNET_4_6;
    private static final long MAX_TOKENS = 4096L;

    private final AnthropicClient client;
    private String systemPromptOverride = null;

    public ClaudeService() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "ANTHROPIC_API_KEY environment variable is not set. " +
                "In IntelliJ: Run → Edit Configurations → Environment Variables."
            );
        }
        // fromEnv() reads ANTHROPIC_API_KEY automatically
        this.client = AnthropicOkHttpClient.fromEnv();
        log.info("ClaudeService initialised. Model: {}", MODEL);
    }

    // -------------------------------------------------------------------------
    // PUBLIC API
    // -------------------------------------------------------------------------

    /** Override the system prompt — used by TestCaseGenerator to inject PageRegistry names. */
    public void setSystemPrompt(String prompt) {
        this.systemPromptOverride = prompt;
    }

    /**
     * First attempt: generate test cases from a user story.
     *
     * @param userStory  plain-English user story text
     * @param baseUrl    base URL for the system under test
     * @return raw JSON string from Claude (not yet validated)
     */
    public String generateTestCases(String userStory, String baseUrl) {
        log.info("Sending generation request to Claude. Story length: {} chars", userStory.length());
        long start = System.currentTimeMillis();

        String sysPrompt = systemPromptOverride != null ? systemPromptOverride : PromptTemplates.systemPrompt();
        String raw = callClaude(sysPrompt, PromptTemplates.userPrompt(userStory, baseUrl));

        log.info("Claude responded in {}ms. Response length: {} chars",
                System.currentTimeMillis() - start, raw.length());
        return raw;
    }

    /**
     * Retry: send the validation error back to Claude and ask it to fix the JSON.
     *
     * @param userStory       original story (for context)
     * @param baseUrl         base URL for the SUT
     * @param validationError the error from our JSON validator
     * @return raw JSON string (second attempt)
     */
    public String retryWithError(String userStory, String baseUrl, String validationError) {
        log.warn("Retrying Claude call. Validation error was: {}", validationError);

        String sysPrompt = systemPromptOverride != null ? systemPromptOverride : PromptTemplates.systemPrompt();
        String raw = callClaude(sysPrompt, PromptTemplates.retryPrompt(userStory, baseUrl, validationError));

        log.info("Retry response received. Length: {} chars", raw.length());
        return raw;
    }

    // -------------------------------------------------------------------------
    // PRIVATE HELPERS
    // -------------------------------------------------------------------------

    private String callClaude(String systemPrompt, String userMessage) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .system(systemPrompt)
                .addUserMessage(userMessage)
                .build();

        Message response = client.messages().create(params);
        return extractText(response);
    }

    /**
     * Extracts the text content from Claude's response.
     * Strips markdown code fences if Claude adds them despite instructions.
     */
    private String extractText(Message response) {
        String raw = response.content().stream()
                .filter(block -> block.isText())
                .findFirst()
                .map(block -> block.asText().text())
                .orElseThrow(() -> new RuntimeException(
                        "Claude returned no text content. Full response: " + response));

        // Strip markdown fences — hallucination guard
        raw = raw.trim();
        if (raw.startsWith("```")) {
            raw = raw.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
            log.warn("Stripped markdown fences from Claude response (hallucination guard triggered)");
        }

        return raw;
    }
}
