package com.qa.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wrapper returned by the generator. Holds all Claude-generated test cases
 * plus metadata about the generation run (model used, retry count, timestamp).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestSuite {

    // From Claude's JSON output
    private List<TestCase> tests;

    // Enriched by the generator at runtime
    private String storySource;
    private String modelUsed;
    private String generatedAt;
    private int retryCount;
    private String rawClaudeJson;

    public TestSuite() {
        this.generatedAt = Instant.now().toString();
    }

    // -------- Convenience methods --------

    public List<TestCase> getUiTests() {
        return tests.stream().filter(TestCase::isUiTest).collect(Collectors.toList());
    }

    public List<TestCase> getApiTests() {
        return tests.stream().filter(TestCase::isApiTest).collect(Collectors.toList());
    }

    public int totalCount()   { return tests == null ? 0 : tests.size(); }
    public int uiCount()      { return (int) tests.stream().filter(TestCase::isUiTest).count();  }
    public int apiCount()     { return (int) tests.stream().filter(TestCase::isApiTest).count(); }

    // -------- Getters & Setters --------

    public List<TestCase> getTests() { return tests; }
    public void setTests(List<TestCase> tests) { this.tests = tests; }

    public String getStorySource()  { return storySource;  }
    public void setStorySource(String s) { this.storySource = s; }

    public String getModelUsed()    { return modelUsed;    }
    public void setModelUsed(String m) { this.modelUsed = m; }

    public String getGeneratedAt()  { return generatedAt;  }
    public void setGeneratedAt(String t) { this.generatedAt = t; }

    public int getRetryCount()      { return retryCount;   }
    public void setRetryCount(int r) { this.retryCount = r; }

    public String getRawClaudeJson() { return rawClaudeJson; }
    public void setRawClaudeJson(String j) { this.rawClaudeJson = j; }

    @Override
    public String toString() {
        return String.format("TestSuite[total=%d, ui=%d, api=%d, retries=%d, model=%s]",
                totalCount(), uiCount(), apiCount(), retryCount, modelUsed);
    }
}
