package com.qa.ai.runner;

import com.qa.ai.base.BaseTest;

/**
 * Runs AI-generated UI tests for the SauceDemo add-to-cart flow.
 * Covers adding from inventory page, adding from product detail page,
 * cart badge count verification, and cart page navigation.
 * Story: src/main/resources/stories/generated/add-to-cart-verification-tests.json
 */
public class AITestRunner_AddToCartTests extends BaseTest {

    private static final String STORY_FILE = "src/main/resources/stories/generated/add-to-cart-verification-tests.json";
    private static final String BASE_URL   = "https://www.saucedemo.com";

    @Override protected String getStoryFilePath() { return STORY_FILE; }
    @Override protected String getBaseUrl()       { return BASE_URL; }
    @Override protected String getModuleName()    { return "Add to Cart"; }
}