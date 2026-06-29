package com.qa.ai.reporter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Persists a lightweight summary of every test run to test-history/runs.json.
 *
 * Stored OUTSIDE target/ so it survives mvn clean.
 * Accumulates across runs — drives the local trend dashboard.
 *
 * Schema per entry:
 * {
 *   "runId":         "3F2A8B1C",
 *   "timestamp":     "2026-05-11T18:23:36Z",
 *   "suite":         "Products Page",
 *   "passed":        7,
 *   "failed":        0,
 *   "skipped":       0,
 *   "selfHeals":     4,
 *   "unknownTargets":0,
 *   "coveragePct":   63
 * }
 */
public class RunHistoryStore {

    private static final Logger log  = LogManager.getLogger(RunHistoryStore.class);
    private static final Path   FILE = Paths.get("test-history", "runs.json");

    /** Maximum entries kept in runs.json before oldest are dropped. Exposed for dashboard warning. */
    public static final int MAX_RUNS = 50;

    private RunHistoryStore() {}

    /**
     * Appends the current run's summary to test-history/runs.json.
     *
     * @param suite         module name (e.g. "Login Page")
     * @param passed        number of passing tests
     * @param failed        number of failing tests
     * @param skipped       number of skipped tests
     * @param selfHeals     total self-heal events this run
     * @param unknownTargets count of unresolved logical names
     * @param coveragePct   PageRegistry coverage percentage
     */
    public static void record(String suite, int passed, int failed, int skipped,
                               int selfHeals, int unknownTargets, int coveragePct,
                               String reportFile) {
        record(suite, passed, failed, skipped, selfHeals, unknownTargets, coveragePct, 0, reportFile);
    }

    public static void record(String suite, int passed, int failed, int skipped,
                               int selfHeals, int unknownTargets, int coveragePct,
                               int qualityScore, String reportFile) {
        try {
            Files.createDirectories(FILE.getParent());
            List<String> existing = loadExistingEntries();

            String entry = String.format(
                "  {\"runId\":\"%s\",\"timestamp\":\"%s\",\"suite\":\"%s\"," +
                "\"passed\":%d,\"failed\":%d,\"skipped\":%d," +
                "\"selfHeals\":%d,\"unknownTargets\":%d,\"coveragePct\":%d," +
                "\"qualityScore\":%d,\"reportFile\":\"%s\"}",
                RunContext.getRunId(), RunContext.getTimestamp(),
                jsonEsc(suite), passed, failed, skipped,
                selfHeals, unknownTargets, coveragePct,
                qualityScore, jsonEsc(reportFile));

            existing.add(entry);
            if (existing.size() > MAX_RUNS) existing = existing.subList(existing.size() - MAX_RUNS, existing.size());

            String json = "[\n" + String.join(",\n", existing) + "\n]\n";
            Files.writeString(FILE, json, StandardCharsets.UTF_8);
            log.info("Run history updated → {} ({} total entries)", FILE, existing.size());

        } catch (IOException e) {
            log.warn("Could not update run history: {}", e.getMessage());
        }
    }

    /** Reads all run entries as raw JSON object strings. */
    public static List<Map<String, String>> loadRuns() {
        List<Map<String, String>> runs = new ArrayList<>();
        if (!Files.exists(FILE)) return runs;
        try {
            String content = Files.readString(FILE, StandardCharsets.UTF_8);
            // Simple regex extraction — avoids a Jackson dependency cycle
            Pattern entry = Pattern.compile("\\{([^}]+)}");
            Matcher m = entry.matcher(content);
            while (m.find()) {
                runs.add(parseEntry(m.group(1)));
            }
        } catch (IOException e) {
            log.warn("Could not read run history: {}", e.getMessage());
        }
        return runs;
    }

    // -------------------------------------------------------------------------
    // PRIVATE HELPERS
    // -------------------------------------------------------------------------

    private static List<String> loadExistingEntries() throws IOException {
        if (!Files.exists(FILE)) return new ArrayList<>();
        String content = Files.readString(FILE, StandardCharsets.UTF_8).trim();
        if (content.isEmpty() || content.equals("[]")) return new ArrayList<>();
        // Extract each {...} block
        List<String> entries = new ArrayList<>();
        Pattern p = Pattern.compile("\\{[^}]+}");
        Matcher m = p.matcher(content);
        while (m.find()) entries.add("  " + m.group().trim());
        return entries;
    }

    private static Map<String, String> parseEntry(String body) {
        Map<String, String> map = new LinkedHashMap<>();
        Pattern kv = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"?([^\",}]+)\"?");
        Matcher m = kv.matcher(body);
        while (m.find()) map.put(m.group(1), m.group(2).trim());
        return map;
    }

    private static String jsonEsc(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}