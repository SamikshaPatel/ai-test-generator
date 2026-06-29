
package com.qa.ai.reporter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates test-history/trend-dashboard.html after each run.
 *
 * Uses Chart.js (CDN) to render:
 *   - Pass / Fail trend over last N runs
 *   - Self-heal event count trend
 *   - PageRegistry coverage % trend
 *   - Run summary table
 *
 * Open test-history/trend-dashboard.html in any browser — no server needed.
 * Requires internet for Chart.js CDN on first open (or embed offline).
 */
public class TrendDashboard {

    private static final Logger log  = LogManager.getLogger(TrendDashboard.class);
    private static final Path   FILE = Paths.get("test-history", "trend-dashboard.html");

    private TrendDashboard() {}

    public static void generate() {
        List<Map<String, String>> runs = RunHistoryStore.loadRuns();
        if (runs.isEmpty()) {
            log.debug("No run history yet — skipping trend dashboard generation");
            return;
        }

        String labels     = toJsArray(runs, "timestamp",    r -> shorten(r));
        String passed     = toJsArray(runs, "passed",       r -> r);
        String failed     = toJsArray(runs, "failed",       r -> r);
        String selfHeals  = toJsArray(runs, "selfHeals",    r -> r);
        String coverage   = toJsArray(runs, "coveragePct",  r -> r);
        String quality    = toJsArray(runs, "qualityScore", r -> r.equals("0") ? "0" : r);
        String runIds     = toJsArray(runs, "runId",        r -> r);
        String suites     = toJsArray(runs, "suite",        r -> r);

        String html = buildHtml(labels, passed, failed, selfHeals, coverage, quality, runIds, suites, runs);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, html, StandardCharsets.UTF_8);
            log.info("Trend dashboard updated → {} ({} runs)", FILE.toAbsolutePath(), runs.size());
        } catch (IOException e) {
            log.warn("Could not write trend dashboard: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // HTML GENERATION
    // -------------------------------------------------------------------------

    private static String buildHtml(String labels, String passed, String failed,
                                     String selfHeals, String coverage, String quality,
                                     String runIds, String suites,
                                     List<Map<String, String>> runs) {
        String tableRows = runs.stream().map(r -> {
            String rf = r.getOrDefault("reportFile", "");
            String reportLink = rf.isEmpty()
                ? ""
                : "<a href=\"" + rf + "\" target=\"_blank\" style=\"color:#a78bfa;text-decoration:none\">"
                  + "&#128196; Report</a>";
            int qs = intVal(r, "qualityScore");
            String qsClass = qs >= 75 ? "pass" : qs >= 50 ? "heal" : qs > 0 ? "fail" : "";
            return String.format(
                "<tr>"
                + "<td>%s</td>"
                + "<td>%s</td>"
                + "<td>%s</td>"
                + "<td class=\"pass\">%s</td>"
                + "<td class=\"%s\">%s</td>"
                + "<td class=\"%s\">%s</td>"
                + "<td>%s%%</td>"
                + "<td class=\"%s\">%s</td>"
                + "<td>%s</td>"
                + "</tr>",
                r.getOrDefault("runId", ""),
                r.getOrDefault("timestamp", ""),
                r.getOrDefault("suite", ""),
                r.getOrDefault("passed", "0"),
                intVal(r, "failed") > 0 ? "fail" : "",    r.getOrDefault("failed", "0"),
                intVal(r, "selfHeals") > 0 ? "heal" : "", r.getOrDefault("selfHeals", "0"),
                r.getOrDefault("coveragePct", "0"),
                qsClass, qs > 0 ? qs + "/100" : "—",
                reportLink);
        }).collect(Collectors.joining("\n"));

        String template = "<!DOCTYPE html>\n"
            + "<html lang=\"en\">\n"
            + "<head>\n"
            + "  <meta charset=\"UTF-8\"/>\n"
            + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"/>\n"
            + "  <title>AI Test Generator \u2014 Trend Dashboard</title>\n"
            + "  <script src=\"https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js\"></script>\n"
            + "  <style>\n"
            + "    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;\n"
            + "           background: #0f1117; color: #e2e8f0; margin: 0; padding: 20px; }\n"
            + "    h1   { color: #7c3aed; margin-bottom: 4px; }\n"
            + "    .sub { color: #64748b; margin-bottom: 30px; font-size: 0.9em; }\n"
            + "    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 30px; }\n"
            + "    .card { background: #1e2130; border-radius: 12px; padding: 20px;\n"
            + "            border: 1px solid #2d3148; }\n"
            + "    .card h3 { margin: 0 0 16px 0; color: #a78bfa; font-size: 0.95em;\n"
            + "               text-transform: uppercase; letter-spacing: 0.05em; }\n"
            + "    .card canvas { min-height: 220px; }\n"
            + "    table { width: 100%; border-collapse: collapse; font-size: 0.85em; }\n"
            + "    th    { background: #2d3148; padding: 10px 12px; text-align: left;\n"
            + "            color: #a78bfa; font-weight: 600; }\n"
            + "    td    { padding: 9px 12px; border-bottom: 1px solid #1a1d2e; }\n"
            + "    tr:hover td { background: #252840; }\n"
            + "    .pass { color: #4ade80; font-weight: 600; }\n"
            + "    .fail { color: #f87171; font-weight: 600; }\n"
            + "    .heal { color: #fbbf24; font-weight: 600; }\n"
            + "    .stat-row { display: flex; gap: 16px; margin-bottom: 24px; }\n"
            + "    .stat     { background: #1e2130; border-radius: 10px; padding: 16px 24px;\n"
            + "                border: 1px solid #2d3148; flex: 1; text-align: center; }\n"
            + "    .stat .val { font-size: 2em; font-weight: 700; color: #7c3aed; }\n"
            + "    .stat .lbl { font-size: 0.8em; color: #64748b; margin-top: 4px; }\n"
            + "    .warn { display:none; border-radius:8px; padding:12px 18px; margin-bottom:20px;\n"
            + "            font-size:0.9em; font-weight:500; border-left:4px solid; }\n"
            + "    .warn-yellow { background:#422006; border-color:#fbbf24; color:#fde68a; }\n"
            + "    .warn-red    { background:#450a0a; border-color:#f87171; color:#fecaca; }\n"
            + "  </style>\n"
            + "</head>\n"
            + "<body>\n"
            + "  <h1>AI Test Generator \u2014 Trend Dashboard</h1>\n"
            + "  <p class=\"sub\">Local run history \u00b7 auto-generated after each mvn test \u00b7 open test-history/trend-dashboard.html in browser</p>\n"
            + "\n"
            + "  <div class=\"stat-row\">\n"
            + "    <div class=\"stat\"><div class=\"val\" id=\"totalRuns\">\u2014</div><div class=\"lbl\">Runs Evaluated (of __MAX_RUNS__)</div></div>\n"
            + "    <div class=\"stat\"><div class=\"val\" id=\"latestPass\" style=\"color:#4ade80\">\u2014</div><div class=\"lbl\">Latest Passed</div></div>\n"
            + "    <div class=\"stat\"><div class=\"val\" id=\"latestFail\" style=\"color:#f87171\">\u2014</div><div class=\"lbl\">Latest Failed</div></div>\n"
            + "    <div class=\"stat\"><div class=\"val\" id=\"totalHeals\" style=\"color:#fbbf24\">\u2014</div><div class=\"lbl\">Total Self-Heals</div></div>\n"
            + "    <div class=\"stat\"><div class=\"val\" id=\"latestCov\">\u2014</div><div class=\"lbl\">Latest Coverage %</div></div>\n"
            + "  </div>\n"
            + "  <div id=\"capacityWarn\" class=\"warn\"></div>\n"
            + "\n"
            + "  <div class=\"grid\">\n"
            + "    <div class=\"card\">\n"
            + "      <h3>Pass / Fail Trend</h3>\n"
            + "      <canvas id=\"passFailChart\"></canvas>\n"
            + "    </div>\n"
            + "    <div class=\"card\">\n"
            + "      <h3>Self-Heal Events per Run</h3>\n"
            + "      <canvas id=\"healChart\"></canvas>\n"
            + "    </div>\n"
            + "    <div class=\"card\">\n"
            + "      <h3>Page Registry Coverage %</h3>\n"
            + "      <canvas id=\"coverageChart\"></canvas>\n"
            + "    </div>\n"
            + "    <div class=\"card\">\n"
            + "      <h3>AI Test Quality Score (0\u2013100)</h3>\n"
            + "      <canvas id=\"qualityChart\"></canvas>\n"
            + "    </div>\n"
            + "    <div class=\"card\" style=\"grid-column: 1 / -1\">\n"
            + "      <h3>Run History</h3>\n"
            + "      <table>\n"
            + "        <thead><tr>\n"
            + "          <th>Run ID</th><th>Timestamp</th><th>Suite</th>\n"
            + "          <th>Passed</th><th>Failed</th><th>Self-Heals</th><th>Coverage</th><th>Quality</th><th>Report</th>\n"
            + "        </tr></thead>\n"
            + "        <tbody>\n"
            + "__TABLE_ROWS__\n"
            + "        </tbody>\n"
            + "      </table>\n"
            + "    </div>\n"
            + "  </div>\n"
            + "\n"
            + "  <script>\n"
            + "    const labels    = __LABELS__;\n"
            + "    const passed    = __PASSED__;\n"
            + "    const failed    = __FAILED__;\n"
            + "    const selfHeals = __SELF_HEALS__;\n"
            + "    const coverage  = __COVERAGE__;\n"
            + "    const quality   = __QUALITY__;\n"
            + "    // __RUN_IDS__ and __SUITES__ injected below in stat-card block\n"
            + "\n"
            + "    const MAX_RUNS = __MAX_RUNS__;\n"
            + "    const runCount = labels.length;\n"
            + "\n"
            + "    // Aggregate last run by runId (multiple modules share same runId)\n"
            + "    const runIds    = __RUN_IDS__;\n"
            + "    const suites    = __SUITES__;\n"
            + "    const lastRunId = runIds.at(-1);\n"
            + "    const lastRunIdxs = runIds.reduce((acc,id,i) => { if(id===lastRunId) acc.push(i); return acc; }, []);\n"
            + "    const lastRunPassed = lastRunIdxs.reduce((s,i) => s + (passed[i]||0), 0);\n"
            + "    const lastRunFailed = lastRunIdxs.reduce((s,i) => s + (failed[i]||0), 0);\n"
            + "    const lastRunCov    = Math.round(lastRunIdxs.reduce((s,i) => s + (coverage[i]||0), 0) / lastRunIdxs.length);\n"
            + "\n"
            + "    // Stat cards\n"
            + "    document.getElementById('totalRuns').textContent  = runCount;\n"
            + "    document.getElementById('latestPass').textContent = lastRunPassed;\n"
            + "    document.getElementById('latestFail').textContent = lastRunFailed;\n"
            + "    document.getElementById('totalHeals').textContent = selfHeals.reduce((a,b)=>a+b,0);\n"
            + "    document.getElementById('latestCov').textContent  = lastRunCov + '%';\n"
            + "    if (lastRunFailed > 0) document.getElementById('latestFail').style.color = '#f87171';\n"
            + "\n"
            + "    // Capacity warning\n"
            + "    const warn = document.getElementById('capacityWarn');\n"
            + "    if (runCount >= MAX_RUNS) {\n"
            + "      warn.className = 'warn warn-red';\n"
            + "      warn.textContent = '\u26a0\ufe0f Run history is at capacity (' + runCount + ' / ' + MAX_RUNS\n"
            + "        + '). Oldest entries are being dropped automatically. ACTION REQUIRED: archive or export'\n"
            + "        + ' test-history/runs.json to retain full trend data.';\n"
            + "      warn.style.display = 'block';\n"
            + "    } else if (runCount >= Math.ceil(MAX_RUNS / 2)) {\n"
            + "      warn.className = 'warn warn-yellow';\n"
            + "      warn.textContent = '\u26a0\ufe0f ' + runCount + ' of ' + MAX_RUNS\n"
            + "        + ' run history slots used (50% threshold reached). Management should review archiving'\n"
            + "        + ' policy before the limit is reached and oldest data begins to drop.';\n"
            + "      warn.style.display = 'block';\n"
            + "    }\n"
            + "\n"
            + "    const gridColor = '#2d3148';\n"
            + "    const defaults  = { responsive: true, plugins: { legend: { labels: { color: '#e2e8f0' } } },\n"
            + "                        scales: { x: { ticks: { color:'#64748b' }, grid: { color: gridColor } },\n"
            + "                                  y: { ticks: { color:'#64748b' }, grid: { color: gridColor } } } };\n"
            + "\n"
            + "    new Chart(document.getElementById('passFailChart'), {\n"
            + "      type: 'bar',\n"
            + "      data: {\n"
            + "        labels,\n"
            + "        datasets: [\n"
            + "          { label: 'Passed', data: passed,  backgroundColor: '#4ade8088' },\n"
            + "          { label: 'Failed', data: failed,  backgroundColor: '#f8717188' }\n"
            + "        ]\n"
            + "      },\n"
            + "      options: { ...defaults, scales: { ...defaults.scales, y: { ...defaults.scales.y, beginAtZero: true } } }\n"
            + "    });\n"
            + "\n"
            + "    new Chart(document.getElementById('healChart'), {\n"
            + "      type: 'line',\n"
            + "      data: {\n"
            + "        labels,\n"
            + "        datasets: [{ label: 'Self-Heals', data: selfHeals,\n"
            + "          borderColor: '#fbbf24', backgroundColor: '#fbbf2422',\n"
            + "          tension: 0.3, fill: true, pointBackgroundColor: '#fbbf24' }]\n"
            + "      },\n"
            + "      options: defaults\n"
            + "    });\n"
            + "\n"
            + "    new Chart(document.getElementById('coverageChart'), {\n"
            + "      type: 'line',\n"
            + "      data: {\n"
            + "        labels,\n"
            + "        datasets: [{ label: 'Coverage %', data: coverage,\n"
            + "          borderColor: '#7c3aed', backgroundColor: '#7c3aed22',\n"
            + "          tension: 0.3, fill: true, pointBackgroundColor: '#7c3aed' }]\n"
            + "      },\n"
            + "      options: { ...defaults,\n"
            + "        scales: { ...defaults.scales, y: { ...defaults.scales.y, min: 0, max: 100 } } }\n"
            + "    });\n"
            + "\n"
            + "    new Chart(document.getElementById('qualityChart'), {\n"
            + "      type: 'line',\n"
            + "      data: {\n"
            + "        labels,\n"
            + "        datasets: [{ label: 'AI Quality Score', data: quality,\n"
            + "          borderColor: '#34d399', backgroundColor: '#34d39922',\n"
            + "          tension: 0.3, fill: true, pointBackgroundColor: '#34d399' }]\n"
            + "      },\n"
            + "      options: { ...defaults,\n"
            + "        scales: { ...defaults.scales, y: { ...defaults.scales.y, min: 0, max: 100,\n"
            + "          title: { display: true, text: 'Score / 100', color: '#64748b' } } } }\n"
            + "    });\n"
            + "  </script>\n"
            + "</body>\n"
            + "</html>\n";

        return template
            .replace("__TABLE_ROWS__", tableRows)
            .replace("__LABELS__",     labels)
            .replace("__PASSED__",     passed)
            .replace("__FAILED__",     failed)
            .replace("__SELF_HEALS__", selfHeals)
            .replace("__COVERAGE__",   coverage)
            .replace("__QUALITY__",    quality)
            .replace("__RUN_IDS__",    runIds)
            .replace("__SUITES__",     suites)
            .replace("__MAX_RUNS__",   String.valueOf(RunHistoryStore.MAX_RUNS));
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    private static String toJsArray(List<Map<String, String>> runs, String key,
                                     java.util.function.UnaryOperator<String> transform) {
        String items = runs.stream()
            .map(r -> r.getOrDefault(key, "0"))
            .map(transform)
            .map(v -> isNumeric(v) ? v : "\"" + v + "\"")
            .collect(Collectors.joining(", "));
        return "[" + items + "]";
    }

    private static String shorten(String timestamp) {
        // "2026-05-11T18:23:36Z" → "05-11 18:23"
        if (timestamp.length() >= 16) return timestamp.substring(5, 16).replace("T", " ");
        return timestamp;
    }

    private static boolean isNumeric(String s) {
        try { Double.parseDouble(s); return true; } catch (NumberFormatException e) { return false; }
    }

    private static int intVal(Map<String, String> r, String key) {
        try { return Integer.parseInt(r.getOrDefault(key, "0")); } catch (NumberFormatException e) { return 0; }
    }
}