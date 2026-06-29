package com.qa.ai.reporter;

import com.qa.ai.config.ConfigManager;
import com.qa.ai.model.TestSuite;
import com.qa.ai.pages.PageRegistry;
import io.qameta.allure.Allure;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Allure attachment helpers + run-level artifact writers.
 *
 * Writes to target/allure-results/:
 *   categories.json           — failure classification buckets
 *   environment.properties    — run context for Allure Overview widget
 *   executor.json             — CI / run identity for Allure Overview widget (enriched with RunContext)
 *   history/                  — preserved from previous report to enable Trend charts
 *   agent-activity-{stem}.txt — human-readable agent activity report (unique per run)
 *   agent-activity-{stem}.json— machine-readable agent activity report (CI-parseable)
 */
public class AllureReporter {

    private static final Logger log = LogManager.getLogger(AllureReporter.class);
    private static final Path ALLURE_RESULTS = Paths.get(
            System.getProperty("allure.results.directory", "target/allure-results"));
    private static final Path ALLURE_REPORT  = Paths.get("target", "site", "allure-maven-plugin");

    private AllureReporter() {}

    // -------------------------------------------------------------------------
    // PER-SUITE ATTACHMENTS
    // -------------------------------------------------------------------------

    public static void attachSuiteMetadata(TestSuite suite) {
        attach("Claude Raw JSON Output", "application/json", suite.getRawClaudeJson());
        attach("Generation Summary",     "text/plain",       buildSummary(suite));
        log.debug("Allure suite attachments added (model={})", suite.getModelUsed());
    }

    public static void attachTestCaseJson(String testId, String json) {
        attach("Test Case JSON — " + testId, "application/json", json);
    }

    public static void attachPromptUsed(String systemPrompt, String userPrompt) {
        String combined = "=== SYSTEM PROMPT ===\n" + systemPrompt +
                          "\n\n=== USER PROMPT ===\n" + userPrompt;
        attach("Claude Prompts Used", "text/plain", combined);
    }

    // -------------------------------------------------------------------------
    // AGENT ACTIVITY REPORT — run-stamped, dual format
    // -------------------------------------------------------------------------

    /**
     * Generates human-readable + machine-readable agent activity reports.
     *
     * Writes to three locations:
     *   target/allure-results/   — picked up by Allure as attachment
     *   target/agent-reports/    — dedicated folder for easy discovery
     *   test-history/agent-reports/ — persistent copy for trend dashboard links (survives mvn clean)
     *
     * @return relative path "agent-reports/{filename}.html" for use in trend dashboard links
     */
    public static String attachAgentActivityReport(String suiteName) {
        Set<String> registry = PageRegistry.allSelectors().keySet();
        String moduleSlug = suiteName.replaceAll("[^a-zA-Z0-9_-]", "_");
        String stem = RunContext.getRunId() + "-" + moduleSlug;

        String textReport = AgentActivity.get().buildReport(suiteName, registry);
        String jsonReport = AgentActivity.get().toJson(suiteName, registry);
        String htmlReport = buildAgentHtml(suiteName, textReport);

        // 1. Attach to Allure (visible in report UI)
        attach("Agent Activity — " + suiteName + " [" + RunContext.getRunId() + "]",
               "text/plain", textReport);

        // 2. Write to target/allure-results/ (raw files for CI artifact upload)
        writeFile(ALLURE_RESULTS.resolve("agent-activity-" + stem + ".txt"),  textReport);
        writeFile(ALLURE_RESULTS.resolve("agent-activity-" + stem + ".json"), jsonReport);

        // 3. Write to target/agent-reports/ — dedicated, easy-to-find folder
        Path agentReportsDir = Paths.get("target", "agent-reports");
        writeFile(agentReportsDir.resolve(stem + ".json"), jsonReport);
        writeFile(agentReportsDir.resolve(stem + ".html"), htmlReport);

        // 4. Copy HTML to test-history/agent-reports/ — survives mvn clean, linked from dashboard
        Path historyAgentDir = Paths.get("test-history", "agent-reports");
        writeFile(historyAgentDir.resolve(stem + ".html"), htmlReport);

        log.info("Agent activity reports written → target/agent-reports/{}.html (runId={})", stem, RunContext.getRunId());
        return "agent-reports/" + stem + ".html";
    }

    private static String buildAgentHtml(String suiteName, String textReport) {
        String escaped = textReport
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n"
            + "  <meta charset=\"UTF-8\"/>\n"
            + "  <title>Agent Activity \u2014 " + suiteName + "</title>\n"
            + "  <style>\n"
            + "    body { background:#0f1117; color:#e2e8f0; font-family:monospace; padding:24px; margin:0; }\n"
            + "    h2   { color:#7c3aed; margin-bottom:4px; }\n"
            + "    .sub { color:#64748b; font-size:0.85em; margin-bottom:20px; }\n"
            + "    pre  { background:#1e2130; border:1px solid #2d3148; border-radius:8px;\n"
            + "           padding:20px; white-space:pre-wrap; word-break:break-word;\n"
            + "           font-size:0.88em; line-height:1.6; }\n"
            + "  </style>\n</head>\n<body>\n"
            + "  <h2>Agent Activity Report \u2014 " + suiteName + "</h2>\n"
            + "  <p class=\"sub\">Run: " + RunContext.getLabel() + "</p>\n"
            + "  <pre>" + escaped + "</pre>\n"
            + "</body>\n</html>\n";
    }

    // -------------------------------------------------------------------------
    // H — ALLURE ENVIRONMENT PANEL
    // -------------------------------------------------------------------------

    public static void writeAllureEnvironment() {
        String env = buildEnvironmentProperties();
        writeFile(ALLURE_RESULTS.resolve("environment.properties"), env);
        log.info("Wrote allure environment.properties (run={})", RunContext.getRunId());
    }

    // -------------------------------------------------------------------------
    // H — ALLURE FAILURE CATEGORIES
    // -------------------------------------------------------------------------

    public static void writeAllureCategories() {
        String json = """
                [
                  {
                    "name": "Element Not Found / Timeout",
                    "matchedStatuses": ["failed", "broken"],
                    "messageRegex": ".*TimeoutError.*|.*waiting for selector.*|.*not found.*|.*timeout exceeded.*"
                  },
                  {
                    "name": "Assertion Failures",
                    "matchedStatuses": ["failed"],
                    "messageRegex": ".*AssertionError.*|.*Expected.*but was.*|.*to contain.*|.*to be visible.*"
                  },
                  {
                    "name": "Self-Heal Succeeded",
                    "matchedStatuses": ["passed"],
                    "messageRegex": ".*Self-Heal.*"
                  },
                  {
                    "name": "Infrastructure / Broken Tests",
                    "matchedStatuses": ["broken"],
                    "messageRegex": "^(?!.*TimeoutError|.*waiting for selector|.*not found|.*timeout exceeded).*"
                  }
                ]
                """;
        writeFile(ALLURE_RESULTS.resolve("categories.json"), json);
        log.info("Wrote allure categories.json");
    }

    // -------------------------------------------------------------------------
    // EXECUTOR.JSON — enriched with run identity (visible on Allure Overview)
    // -------------------------------------------------------------------------

    public static void writeExecutorInfo() {
        String json = String.format("""
                {
                  "name": "AI Test Generator",
                  "type": "local",
                  "buildName": "RUN-%s",
                  "buildOrder": "%s",
                  "reportName": "AI-Powered QA Suite — %s"
                }
                """,
                RunContext.getRunId(),
                RunContext.getTimestampSafe(),
                RunContext.getTimestamp());
        writeFile(ALLURE_RESULTS.resolve("executor.json"), json);
        log.info("Wrote executor.json (runId={})", RunContext.getRunId());
    }

    // -------------------------------------------------------------------------
    // HISTORY PRESERVATION — enables Trend charts between runs
    // -------------------------------------------------------------------------

    /**
     * Copies history/ from the previous Allure HTML report back into allure-results/
     * so that the next report generation includes trend data.
     *
     * Call BEFORE tests run (in warmUp) so the history is present when mvn allure:report runs.
     */
    public static void preserveHistory() {
        Path historySource = ALLURE_REPORT.resolve("history");
        Path historyDest   = ALLURE_RESULTS.resolve("history");
        if (Files.exists(historySource)) {
            try {
                copyDirectory(historySource, historyDest);
                log.info("Preserved Allure history from previous report ({} → {})",
                        historySource, historyDest);
            } catch (IOException e) {
                log.warn("Could not preserve Allure history: {}", e.getMessage());
            }
        } else {
            log.debug("No previous Allure report history found at {} — first run", historySource);
        }
    }

    // -------------------------------------------------------------------------
    // PRIVATE HELPERS
    // -------------------------------------------------------------------------

    private static String buildEnvironmentProperties() {
        ConfigManager cfg = ConfigManager.get();
        return String.join("\n",
            "Run_ID="          + RunContext.getRunId(),
            "Run_Timestamp="   + RunContext.getTimestamp(),
            "Browser="         + cfg.getString("browser", "chromium"),
            "Headless="        + cfg.getBoolean("headless", true),
            "Timeout_ms="      + cfg.getInt("timeout.ms", 10000),
            "Retry_max="       + cfg.getInt("retry.max", 2),
            "Environment="     + cfg.getString("allure.environment.name", "local"),
            "Java_version="    + System.getProperty("java.version"),
            "OS="              + System.getProperty("os.name")
        );
    }

    private static String buildSummary(TestSuite suite) {
        return String.format(
                "AI Test Generation Summary%n" +
                "===========================%n" +
                "Model Used    : %s%n" +
                "Generated At  : %s%n" +
                "Story Source  : %s%n" +
                "Retry Count   : %d%n" +
                "Total Tests   : %d%n" +
                "  UI Tests    : %d%n" +
                "  API Tests   : %d%n",
                suite.getModelUsed(), suite.getGeneratedAt(), suite.getStorySource(),
                suite.getRetryCount(), suite.totalCount(), suite.uiCount(), suite.apiCount());
    }

    private static void writeFile(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Could not write Allure file {}: {}", path, e.getMessage());
        }
    }

    private static void attach(String name, String mimeType, String content) {
        if (content == null || content.isBlank()) return;
        try {
            Allure.addAttachment(name, mimeType,
                    new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                    mimeType.contains("json") ? ".json" : ".txt");
        } catch (Exception e) {
            log.warn("Could not attach '{}' to Allure: {}", name, e.getMessage());
        }
    }

    private static void copyDirectory(Path src, Path dest) throws IOException {
        Files.createDirectories(dest);
        try (var stream = Files.walk(src)) {
            stream.filter(source -> !source.equals(src))   // skip root dir itself
                  .forEach(source -> {
                      try {
                          Path target = dest.resolve(src.relativize(source));
                          if (Files.isDirectory(source)) {
                              Files.createDirectories(target);
                          } else {
                              Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                          }
                      } catch (IOException e) {
                          log.warn("History copy failed for {}: {}", source, e.getMessage());
                      }
                  });
        }
    }

}