package com.qa.ai.runner;

import com.qa.ai.base.BaseTest;

/**
 * Runs AI-generated UI tests for the SauceDemo product detail page.
 * Verifies page load, content visibility (name, description, price),
 * Add to Cart button presence, and back-navigation.
 * Story: src/main/resources/stories/generated/product-detail-verification-tests.json
 */
public class AITestRunner_ProductDetailTests extends BaseTest {

    private static final String STORY_FILE = "src/main/resources/stories/generated/product-detail-verification-tests.json";
    private static final String BASE_URL   = "https://www.saucedemo.com";

    @Override protected String getStoryFilePath() { return STORY_FILE; }
    @Override protected String getBaseUrl()       { return BASE_URL; }
    @Override protected String getModuleName()    { return "Product Detail Page"; }
}