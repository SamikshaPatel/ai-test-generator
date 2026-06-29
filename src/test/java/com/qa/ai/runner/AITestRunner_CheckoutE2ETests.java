package com.qa.ai.runner;

import com.qa.ai.base.BaseTest;

/**
 * End-to-end checkout journey: login → add to cart → checkout → confirm.
 *
 * This suite exercises the full user purchase path across 5 test cases,
 * all tagged "smoke" so they run in the fast CI gate.
 *
 * Demonstrates: cross-page E2E coverage, multi-step Playwright flows,
 * negative validation (missing checkout fields), and confirmation assertions.
 */
public class AITestRunner_CheckoutE2ETests extends BaseTest {

    private static final String STORY_FILE =
            "src/main/resources/stories/generated/checkout-e2e-tests.json"; // FILE MODE
    //      "src/main/resources/stories/checkout-e2e-story.txt";             // API MODE

    private static final String BASE_URL = "https://www.saucedemo.com";

    @Override protected String getStoryFilePath() { return STORY_FILE; }
    @Override protected String getBaseUrl()       { return BASE_URL; }
    @Override protected String getModuleName()    { return "Checkout E2E"; }
}
