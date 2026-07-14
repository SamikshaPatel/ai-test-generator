package com.qa.ai.reporter;

import com.qa.ai.scorer.TestQualityScorer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe collector for agent activity events during a test run.
 *
 * Tracks:
 *   - Locator resolutions    — logical name → CSS selector (known Page Object entries used)
 *   - Unknown targets        — step targets not in PageRegistry (potential missing POM entries)
 *   - Self-heal events       — when a fallback selector succeeded after the primary failed
 *   - Variable substitutions — ${VAR} → resolved value (test data used)
 *
 * Call reset() before each suite. Call buildReport() / toJson() at the end.
 */
public class AgentActivity {

    private static final AgentActivity INSTANCE = new AgentActivity();

    private final Map<String, String>                        resolvedLocators  = new ConcurrentHashMap<>();
    private final Set<String>                               unknownTargets     = ConcurrentHashMap.newKeySet();
    private final List<SelfHealEvent>                       selfHealEvents     = new CopyOnWriteArrayList<>();
    private final Map<String, String>                       resolvedVariables  = new ConcurrentHashMap<>();
    private final List<ApiCallEvent>                        apiCalls           = new CopyOnWriteArrayList<>();
    private final AtomicReference<TestQualityScorer.QualityScore> qualityScore = new AtomicReference<>();
    private final AtomicInteger                             flakeRetries       = new AtomicInteger();

    private AgentActivity() {}

    public static AgentActivity get() { return INSTANCE; }

    public void reset() {
        resolvedLocators.clear();
        unknownTargets.clear();
        selfHealEvents.clear();
        resolvedVariables.clear();
        apiCalls.clear();
        qualityScore.set(null);
        flakeRetries.set(0);
    }

    // -------------------------------------------------------------------------
    // EVENT RECORDING
    // -------------------------------------------------------------------------

    public void recordLocatorResolved(String logicalName, String cssSelector) {
        resolvedLocators.put(logicalName, cssSelector);
    }

    /**
     * Flags a step target that was not found in PageRegistry and was used as raw CSS.
     * Only flags word-based names — not CSS selectors, URLs, or URL fragments.
     */
    public void recordUnknownTarget(String target) {
        if (looksLikeLogicalName(target)) {
            unknownTargets.add(target);
        }
    }

    public void recordSelfHeal(String testId, String action, String primary, String healedWith) {
        selfHealEvents.add(new SelfHealEvent(testId, action, primary, healedWith));
    }

    public void recordVariableResolved(String variable, String resolvedValue) {
        String display = variable.toLowerCase().contains("pass") ? "***" : resolvedValue;
        resolvedVariables.put(variable, display);
    }

    public void recordApiCall(String testId, String method, String url, int statusCode) {
        apiCalls.add(new ApiCallEvent(testId, method, url, statusCode));
    }

    public void recordFlakeRetry() {
        flakeRetries.incrementAndGet();
    }

    public void recordQualityScore(TestQualityScorer.QualityScore score) {
        qualityScore.set(score);
    }

    // -------------------------------------------------------------------------
    // HUMAN-READABLE TEXT REPORT
    // -------------------------------------------------------------------------

    public String buildReport(String suiteName, Set<String> allRegistrySelectors) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════╗\n");
        sb.append("║           AGENT ACTIVITY REPORT                      ║\n");
        sb.append("╚══════════════════════════════════════════════════════╝\n");
        sb.append("Run ID    : ").append(RunContext.getRunId()).append("\n");
        sb.append("Timestamp : ").append(RunContext.getTimestamp()).append("\n");
        sb.append("Suite     : ").append(suiteName).append("\n\n");

        // --- Locator Resolutions ---
        sb.append("── LOCATOR RESOLUTIONS (Page Object logical names used) ──\n");
        if (resolvedLocators.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            resolvedLocators.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(String.format("  %-30s → %s%n", e.getKey(), e.getValue())));
        }
        sb.append("\n");

        // --- Unknown Targets ---
        sb.append("── UNKNOWN TARGETS (not in PageRegistry — add to Page Object) ──\n");
        if (unknownTargets.isEmpty()) {
            sb.append("  (none — all logical names resolved)\n");
        } else {
            unknownTargets.stream().sorted()
                .forEach(t -> sb.append("  ⚠ ").append(t).append("\n"));
        }
        sb.append("\n");

        // --- Self-Heal Events ---
        sb.append("── SELF-HEAL EVENTS (locator drift detected and recovered) ──\n");
        if (selfHealEvents.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            selfHealEvents.forEach(e -> sb.append(String.format(
                "  [%s] %s: %s%n    → healed with: %s%n",
                e.testId(), e.action(), e.primary(), e.healedWith())));
        }
        sb.append("\n");

        // --- Variable Substitutions ---
        sb.append("── TEST DATA VARIABLES RESOLVED ──\n");
        if (resolvedVariables.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            resolvedVariables.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(String.format("  ${%-20s} → %s%n", e.getKey(), e.getValue())));
        }
        sb.append("\n");

        // --- PageRegistry Coverage ---
        sb.append("── PAGE REGISTRY COVERAGE ──\n");
        if (!allRegistrySelectors.isEmpty()) {
            Set<String> used   = resolvedLocators.keySet();
            Set<String> unused = new TreeSet<>(allRegistrySelectors);
            unused.removeAll(used);
            int pct = (int) (100.0 * used.size() / allRegistrySelectors.size());
            sb.append(String.format("  Used   : %d/%d selectors (%d%%)%n",
                    used.size(), allRegistrySelectors.size(), pct));
            if (!unused.isEmpty()) {
                sb.append("  Unused : ").append(String.join(", ", unused)).append("\n");
            }
        } else {
            sb.append("  (PageRegistry is empty)\n");
        }
        sb.append("\n");

        // --- API Calls ---
        if (!apiCalls.isEmpty()) {
            sb.append("── API CALLS EXECUTED ──\n");
            apiCalls.forEach(e -> sb.append(String.format(
                "  [%s] %s %s → HTTP %d%n", e.testId(), e.method(), e.url(), e.statusCode())));
            sb.append("\n");
        }

        // --- AI Quality Score ---
        TestQualityScorer.QualityScore qs = qualityScore.get();
        if (qs != null) {
            sb.append("── AI TEST QUALITY SCORE ──\n");
            sb.append(qs.toReport()).append("\n\n");
        }

        // --- Summary ---
        sb.append("── SUMMARY ──\n");
        sb.append(String.format("  Logical names resolved : %d%n", resolvedLocators.size()));
        sb.append(String.format("  Unknown targets        : %d%n", unknownTargets.size()));
        sb.append(String.format("  Self-heal events       : %d%n", selfHealEvents.size()));
        sb.append(String.format("  Variables resolved     : %d%n", resolvedVariables.size()));
        if (!apiCalls.isEmpty()) {
            sb.append(String.format("  API calls executed     : %d%n", apiCalls.size()));
        }
        if (qs != null) {
            sb.append(String.format("  AI quality score       : %d/100 (%s)%n", qs.total(), qs.tier()));
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // MACHINE-READABLE JSON REPORT (CI-parseable)
    // -------------------------------------------------------------------------

    public String toJson(String suiteName, Set<String> allRegistrySelectors) {
        Set<String> used   = resolvedLocators.keySet();
        Set<String> unused = new TreeSet<>(allRegistrySelectors);
        unused.removeAll(used);
        int total = allRegistrySelectors.size();
        int pct   = total > 0 ? (int)(100.0 * used.size() / total) : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"runId\": \"").append(RunContext.getRunId()).append("\",\n");
        sb.append("  \"timestamp\": \"").append(RunContext.getTimestamp()).append("\",\n");
        sb.append("  \"suite\": \"").append(suiteName).append("\",\n");

        sb.append("  \"resolvedLocators\": {\n");
        List<Map.Entry<String,String>> locEntries = new ArrayList<>(resolvedLocators.entrySet());
        locEntries.sort(Map.Entry.comparingByKey());
        for (int i = 0; i < locEntries.size(); i++) {
            sb.append(String.format("    \"%s\": \"%s\"%s%n",
                jsonEsc(locEntries.get(i).getKey()),
                jsonEsc(locEntries.get(i).getValue()),
                i < locEntries.size()-1 ? "," : ""));
        }
        sb.append("  },\n");

        sb.append("  \"unknownTargets\": [");
        List<String> ut = new ArrayList<>(unknownTargets);
        Collections.sort(ut);
        sb.append(ut.isEmpty() ? "" : "\n");
        for (int i = 0; i < ut.size(); i++) {
            sb.append("    \"").append(jsonEsc(ut.get(i))).append("\"")
              .append(i < ut.size()-1 ? "," : "").append("\n");
        }
        sb.append(ut.isEmpty() ? "" : "  ");
        sb.append("],\n");

        sb.append("  \"selfHealEvents\": [");
        sb.append(selfHealEvents.isEmpty() ? "" : "\n");
        for (int i = 0; i < selfHealEvents.size(); i++) {
            SelfHealEvent e = selfHealEvents.get(i);
            sb.append(String.format(
                "    {\"testId\":\"%s\",\"action\":\"%s\",\"primary\":\"%s\",\"healedWith\":\"%s\"}%s%n",
                jsonEsc(e.testId()), jsonEsc(e.action()), jsonEsc(e.primary()), jsonEsc(e.healedWith()),
                i < selfHealEvents.size()-1 ? "," : ""));
        }
        sb.append(selfHealEvents.isEmpty() ? "" : "  ");
        sb.append("],\n");

        sb.append("  \"resolvedVariables\": {\n");
        List<Map.Entry<String,String>> varEntries = new ArrayList<>(resolvedVariables.entrySet());
        varEntries.sort(Map.Entry.comparingByKey());
        for (int i = 0; i < varEntries.size(); i++) {
            sb.append(String.format("    \"%s\": \"%s\"%s%n",
                jsonEsc(varEntries.get(i).getKey()),
                jsonEsc(varEntries.get(i).getValue()),
                i < varEntries.size()-1 ? "," : ""));
        }
        sb.append("  },\n");

        sb.append("  \"apiCalls\": [");
        sb.append(apiCalls.isEmpty() ? "" : "\n");
        for (int i = 0; i < apiCalls.size(); i++) {
            ApiCallEvent e = apiCalls.get(i);
            sb.append(String.format(
                "    {\"testId\":\"%s\",\"method\":\"%s\",\"url\":\"%s\",\"statusCode\":%d}%s%n",
                jsonEsc(e.testId()), jsonEsc(e.method()), jsonEsc(e.url()), e.statusCode(),
                i < apiCalls.size()-1 ? "," : ""));
        }
        sb.append(apiCalls.isEmpty() ? "" : "  ");
        sb.append("],\n");

        sb.append("  \"coverage\": {\n");
        sb.append("    \"used\": ").append(used.size()).append(",\n");
        sb.append("    \"total\": ").append(total).append(",\n");
        sb.append("    \"percent\": ").append(pct).append(",\n");
        sb.append("    \"unused\": [");
        List<String> unusedList = new ArrayList<>(unused);
        if (!unusedList.isEmpty()) {
            sb.append("\n");
            for (int i = 0; i < unusedList.size(); i++) {
                sb.append("      \"").append(jsonEsc(unusedList.get(i))).append("\"")
                  .append(i < unusedList.size()-1 ? "," : "").append("\n");
            }
            sb.append("    ");
        }
        sb.append("]\n  }\n}\n");

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    /**
     * Returns true only for plain word-style names (letters/underscores/digits).
     * Explicitly rejects: CSS selectors (#, ., [, :), URLs (http/https://), URL fragments (/).
     */
    private boolean looksLikeLogicalName(String target) {
        if (target == null || target.isBlank()) return false;
        // Reject CSS selectors
        char first = target.charAt(0);
        if (first == '#' || first == '.' || first == '[' || first == ':' || first == '/') return false;
        // Reject URLs
        if (target.startsWith("http://") || target.startsWith("https://")) return false;
        // Must start with a letter or underscore and contain only word chars
        return (Character.isLetter(first) || first == '_') &&
               target.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_');
    }

    private String jsonEsc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    // -------------------------------------------------------------------------
    // ACCESSORS
    // -------------------------------------------------------------------------

    public Set<String>                        getUnknownTargets()  { return Collections.unmodifiableSet(unknownTargets); }
    public List<SelfHealEvent>                getSelfHealEvents()  { return Collections.unmodifiableList(selfHealEvents); }
    public Map<String, String>                getResolvedLocators(){ return Collections.unmodifiableMap(resolvedLocators); }
    public List<ApiCallEvent>                 getApiCalls()        { return Collections.unmodifiableList(apiCalls); }
    public int                                selfHealCount()      { return selfHealEvents.size(); }
    public int                                apiCallCount()       { return apiCalls.size(); }
    public int                                flakeRetryCount()    { return flakeRetries.get(); }
    public TestQualityScorer.QualityScore     getQualityScore()    { return qualityScore.get(); }
    public int                                qualityScoreTotal()  { TestQualityScorer.QualityScore qs = qualityScore.get(); return qs != null ? qs.total() : 0; }

    // -------------------------------------------------------------------------
    // EVENT MODEL
    // -------------------------------------------------------------------------

    public record SelfHealEvent(String testId, String action, String primary, String healedWith) {}
    public record ApiCallEvent(String testId, String method, String url, int statusCode) {}
}