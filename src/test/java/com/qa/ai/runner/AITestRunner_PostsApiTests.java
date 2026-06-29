package com.qa.ai.runner;

import com.qa.ai.base.BaseTest;

/**
 * Runs AI-generated API tests for the /posts resource (full CRUD + PATCH + filter).
 * Story: src/main/resources/stories/generated/posts-api-tests.json
 */
public class AITestRunner_PostsApiTests extends BaseTest {

    private static final String STORY_FILE = "src/main/resources/stories/generated/posts-api-tests.json";
    private static final String BASE_URL   = "https://jsonplaceholder.typicode.com";

    @Override protected String getStoryFilePath() { return STORY_FILE; }
    @Override protected String getBaseUrl()       { return BASE_URL; }
    @Override protected String getModuleName()    { return "Posts API"; }
}