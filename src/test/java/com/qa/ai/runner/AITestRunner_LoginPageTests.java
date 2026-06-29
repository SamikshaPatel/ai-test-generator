package com.qa.ai.runner;

import com.qa.ai.base.BaseTest;

/**
 * Runs Claude-generated tests for the SauceDemo Login page.
 *
 * Switch STORY_FILE to the .txt story path (+ set ANTHROPIC_API_KEY) to
 * generate tests live via the Claude API instead of loading from JSON.
 */
public class AITestRunner_LoginPageTests extends BaseTest {

    private static final String STORY_FILE =
            "src/main/resources/stories/generated/login-ui-tests.json"; // FILE MODE
    //      "src/main/resources/stories/user-api-story.txt";             // API MODE

    private static final String BASE_URL = "https://www.saucedemo.com";

    @Override protected String getStoryFilePath() { return STORY_FILE; }
    @Override protected String getBaseUrl()       { return BASE_URL; }
    @Override protected String getModuleName()    { return "Login Page"; }
}