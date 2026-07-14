package com.qa.ai.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qa.ai.executor.PlaywrightExecutor;
import com.qa.ai.executor.RestAssuredExecutor;
import com.qa.ai.generator.TestCaseGenerator;
import com.qa.ai.model.TestCase;
import com.qa.ai.model.TestSuite;
import com.qa.ai.pages.PageRegistry;
import com.qa.ai.reporter.AgentActivity;
import com.qa.ai.reporter.AllureReporter;
import com.qa.ai.reporter.QualityGateChecker;
import com.qa.ai.reporter.RunContext;
import com.qa.ai.reporter.RunHistoryStore;
import com.qa.ai.reporter.TrendDashboard;
import com.qa.ai.config.ConfigManager;
import io.qameta.allure.*;
import io.qameta.allure.model.Label;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for all AI-generated test runners.
 *
 * G — shared @DataProvider, @Test, and helpers live here so each runner
 *     subclass only needs to declare STORY_FILE and BASE_URL.
 * H — warmUp() writes Allure categories.json and environment.properties.
 */
@Epic("AI-Powered QA Portfolio")
@Feature("Claude Test Case Generator")
public abstract class BaseTest {

    protected static final Logger log = LogManager.getLogger(BaseTest.class);

    // Instance-level so each runner subclass gets its own suite
    private volatile TestSuite sharedSuite;
    private final Object LOCK = new Object();

    // Test result counters — updated by @AfterMethod (supports ITestResult injection)
    private final AtomicInteger passedCount  = new AtomicInteger();
    private final AtomicInteger failedCount  = new AtomicInteger();
    private final AtomicInteger skippedCount = new AtomicInteger();

    protected abstract String getStoryFilePath();
    protected abstract String getBaseUrl();

    /** Human-readable module name shown as the Allure Story label — e.g. "Login Page", "Products Page". */
    protected abstract String getModuleName();

    // -------------------------------------------------------------------------
    // TEST SUITE GENERATION
    // -------------------------------------------------------------------------

    protected TestSuite getSuite() {
        if (sharedSuite == null) {
            synchronized (LOCK) {
                if (sharedSuite == null) {
                    TestCaseGenerator generator = new TestCaseGenerator();
                    String path = getStoryFilePath();
                    if (path.endsWith(".json")) {
                        log.info("FILE MODE: loading JSON from {}", path);
                        sharedSuite = generator.generateFromJson(path);
                    } else {
                        log.info("API MODE: generating from story {}", path);
                        sharedSuite = generator.generateFromFile(path, getBaseUrl());
                    }
                    log.info("Suite ready: {}", sharedSuite);
                }
            }
        }
        return sharedSuite;
    }

    @BeforeTest(alwaysRun = true)
    public void warmUp() {
        AgentActivity.get().reset();
        passedCount.set(0);
        failedCount.set(0);
        skippedCount.set(0);
        AllureReporter.preserveHistory();          // copy history/ before results are written
        TestSuite suite = getSuite();
        AllureReporter.attachSuiteMetadata(suite);
        AllureReporter.writeAllureEnvironment();   // environment.properties (with run ID)
        AllureReporter.writeAllureCategories();    // categories.json
        AllureReporter.writeExecutorInfo();        // executor.json (with run ID on Overview)
        log.info("Test suite warmed up: {} | Run: {}", suite, RunContext.getRunId());
    }

    @AfterMethod(alwaysRun = true)
    public void recordResult(ITestResult result) {
        switch (result.getStatus()) {
            case ITestResult.SUCCESS -> passedCount.incrementAndGet();
            case ITestResult.FAILURE -> failedCount.incrementAndGet();
            case ITestResult.SKIP    -> skippedCount.incrementAndGet();
        }
    }

    @AfterTest(alwaysRun = true)
    public void reportAgentActivity() {
        String module = getModuleName();

        // Write agent activity reports — returns relative path for dashboard linking
        String reportFile = AllureReporter.attachAgentActivityReport(module);

        // Compute pass/fail/skipped from tracked counters
        int passed  = passedCount.get();
        int failed  = failedCount.get();
        int skipped = skippedCount.get();

        // Coverage: UI = PageRegistry utilisation; API = pass rate (endpoints exercised)
        int used  = AgentActivity.get().getResolvedLocators().size();
        int total = PageRegistry.allSelectors().size();
        int covPct;
        if (used > 0 && total > 0) {
            covPct = (int)(100.0 * used / total);               // UI: selector coverage
        } else if (AgentActivity.get().apiCallCount() > 0) {
            int ran = passed + failed + skipped;
            covPct = ran > 0 ? (int)(100.0 * passed / ran) : 0; // API: pass rate as coverage proxy
        } else {
            covPct = 0;
        }

        int qualityScore = AgentActivity.get().qualityScoreTotal();

        // Persist run summary for trend dashboard (includes quality score)
        RunHistoryStore.record(module, passed, failed, skipped,
                AgentActivity.get().selfHealCount(),
                AgentActivity.get().getUnknownTargets().size(),
                covPct, qualityScore, reportFile);

        // Evaluate quality gates — fails with QualityGateException if thresholds not met
        try {
            QualityGateChecker.evaluate(module, passed, failed, skipped, AgentActivity.get().flakeRetryCount());
        } catch (QualityGateChecker.QualityGateException gateEx) {
            log.error("Quality gate blocked suite: {} — {}", module, gateEx.getMessage());
            // Re-throw so TestNG marks the @AfterTest as failed (visible in Allure)
            throw gateEx;
        }

        // Regenerate local HTML trend dashboard
        TrendDashboard.generate();

        // Console banner — surfaces self-heal events immediately after the run
        printConsoleBanner(module, passed, failed, skipped, covPct);

        log.info("Post-run reporting complete for: {} [{}]", module, RunContext.getRunId());
    }

    private void printConsoleBanner(String module, int passed, int failed,
                                     int skipped, int covPct) {
        int heals    = AgentActivity.get().selfHealCount();
        int unknowns = AgentActivity.get().getUnknownTargets().size();
        int qScore   = AgentActivity.get().qualityScoreTotal();
        String qTier = AgentActivity.get().getQualityScore() != null
                ? AgentActivity.get().getQualityScore().tier() : "N/A";
        String sep = "═".repeat(58);
        System.out.printf("%n╔%s╗%n", sep);
        System.out.printf("║  %-56s║%n", "AGENT ACTIVITY SUMMARY — " + module);
        System.out.printf("║  %-56s║%n", "Run: " + RunContext.getLabel());
        System.out.printf("╠%s╣%n", sep);
        System.out.printf("║  ✔ Passed         : %-36d║%n", passed);
        System.out.printf("║  ✘ Failed         : %-36d║%n", failed);
        System.out.printf("║  ◌ Skipped        : %-36d║%n", skipped);
        System.out.printf("║  ⚡ Self-Heals     : %-36d║%n", heals);
        System.out.printf("║  ⚠ Unknown Targets: %-36d║%n", unknowns);
        System.out.printf("║  ▣ Coverage       : %-35s║%n", covPct + "%");
        System.out.printf("║  ★ AI Quality     : %-35s║%n", qScore + "/100 (" + qTier + ")");
        System.out.printf("╠%s╣%n", sep);
        System.out.printf("║  Trend dashboard  : test-history/trend-dashboard.html   ║%n");
        System.out.printf("╚%s╝%n%n", sep);
    }

    // -------------------------------------------------------------------------
    // G — DATA PROVIDERS (shared across all runners)
    // -------------------------------------------------------------------------

    @DataProvider(name = "uiTestCases", parallel = true)
    public Object[][] uiTestCases() {
        List<TestCase> uiTests = getSuite().getUiTests();
        if (Boolean.parseBoolean(System.getProperty("smoke.only", "false"))) {
            uiTests = uiTests.stream()
                    .filter(tc -> tc.getTags() != null && tc.getTags().contains("smoke"))
                    .collect(java.util.stream.Collectors.toList());
            log.info("SMOKE MODE: {} UI test cases (tagged 'smoke')", uiTests.size());
        } else {
            log.info("DataProvider: {} UI test cases", uiTests.size());
        }
        return uiTests.stream()
                .map(tc -> new Object[]{tc})
                .toArray(Object[][]::new);
    }

    @DataProvider(name = "apiTestCases", parallel = true)
    public Object[][] apiTestCases() {
        List<TestCase> apiTests = getSuite().getApiTests();
        if (Boolean.parseBoolean(System.getProperty("smoke.only", "false"))) {
            apiTests = apiTests.stream()
                    .filter(tc -> tc.getTags() != null && tc.getTags().contains("smoke"))
                    .collect(java.util.stream.Collectors.toList());
            log.info("SMOKE MODE: {} API test cases (tagged 'smoke')", apiTests.size());
        } else {
            log.info("DataProvider: {} API test cases", apiTests.size());
        }
        return apiTests.stream()
                .map(tc -> new Object[]{tc})
                .toArray(Object[][]::new);
    }

    // -------------------------------------------------------------------------
    // G — SHARED TEST METHODS
    // -------------------------------------------------------------------------

    @Test(dataProvider = "uiTestCases",
          groups = {"ui", "ai-generated"})
    @Severity(SeverityLevel.NORMAL)
    public void runUITest(TestCase testCase) {
        log.info("--- Running UI Test: {} ---", testCase);

        // Set unique test title, story label, and browser parameter from the runner module
        String browser = ConfigManager.get().getString("browser", "chromium");
        Allure.getLifecycle().updateTestCase(tc -> {
            tc.setName("[" + browser + "] " + testCase.getId() + " — " + testCase.getTitle());
            // Override historyId to include browser — prevents Allure deduplicating the same
            // test across browsers (which causes cross-browser failures to be hidden in categories).
            tc.setHistoryId(browser + ":" + getModuleName() + ":" + testCase.getId());
            tc.getLabels().removeIf(l -> "story".equals(l.getName()) || "parentSuite".equals(l.getName()));
            tc.getLabels().add(new Label().setName("parentSuite").setValue("UI Tests"));
            tc.getLabels().add(new Label().setName("story").setValue(getModuleName()));
            tc.getLabels().add(new Label().setName("tag").setValue("browser:" + browser));
        });

        Allure.description(buildDescription(testCase));
        AllureReporter.attachTestCaseJson(testCase.getId(), toJson(testCase));

        int healsBefore = AgentActivity.get().selfHealCount();
        try (PlaywrightExecutor playwright = new PlaywrightExecutor()) {
            playwright.open();
            playwright.execute(testCase);
        }
        // Tag the test result with how many self-heals occurred during this test
        int healsAfter = AgentActivity.get().selfHealCount();
        if (healsAfter > healsBefore) {
            Allure.getLifecycle().updateTestCase(tc ->
                tc.getLabels().add(new Label()
                    .setName("tag")
                    .setValue("self-healed:" + (healsAfter - healsBefore))));
        }
    }

    @Test(dataProvider = "apiTestCases",
          groups = {"api", "ai-generated"})
    @Severity(SeverityLevel.CRITICAL)
    public void runAPITest(TestCase testCase) {
        log.info("--- Running API Test: {} ---", testCase);

        Allure.getLifecycle().updateTestCase(tc -> {
            tc.setName(testCase.getId() + " — " + testCase.getTitle());
            tc.getLabels().removeIf(l -> "story".equals(l.getName()) || "parentSuite".equals(l.getName()));
            tc.getLabels().add(new Label().setName("parentSuite").setValue("API Tests"));
            tc.getLabels().add(new Label().setName("story").setValue(getModuleName()));
        });

        Allure.description(buildDescription(testCase));
        AllureReporter.attachTestCaseJson(testCase.getId(), toJson(testCase));

        RestAssuredExecutor restExecutor = new RestAssuredExecutor();
        restExecutor.execute(testCase);
    }

    // -------------------------------------------------------------------------
    // G — HELPERS
    // -------------------------------------------------------------------------

    private String buildDescription(TestCase tc) {
        return String.format(
                "ID: %s%nType: %s%nTags: %s%n%nExpected outcome:%n%s%n%n" +
                "⚡ This test was generated by Claude AI from a plain-English user story.",
                tc.getId(), tc.getType(), tc.getTags(), tc.getExpected());
    }

    private String toJson(TestCase tc) {
        try {
            return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(tc);
        } catch (Exception e) {
            return "{ \"error\": \"" + e.getMessage() + "\" }";
        }
    }
}