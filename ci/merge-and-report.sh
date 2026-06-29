#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# merge-and-report.sh
#
# Run this after docker-compose finishes to:
#   1. Extract Allure results from the 4 named Docker volumes (chromium, firefox,
#      webkit, api).  The smoke volume is intentionally excluded — the full suite
#      supersedes it and we don't want duplicate/stale entries in the report.
#   2. Merge them into a single target/allure-results directory.
#   3. Generate the combined Allure HTML report.
#
# Volume layout (set by docker-compose.yml):
#   allure-chromium  — UI tests, Chromium
#   allure-firefox   — UI tests, Firefox
#   allure-webkit    — UI tests, WebKit
#   allure-api       — API tests (browser-agnostic, merged once)
#   allure-smoke     — smoke gate results (NOT merged — debug only)
#
# Expected Allure result count: 24 UI × 3 browsers + 29 API × 1 = 103 unique
# test scenarios — correct enterprise view (API is browser-agnostic).
#
# Prerequisites: Docker and Maven must be available on the host.
#
# Usage:
#   ./ci/merge-and-report.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

COMPOSE_PROJECT="${COMPOSE_PROJECT_NAME:-$(basename "$(pwd)")}"
ALLURE_RESULTS="target/allure-results"
REPORT_DIR="target/site/allure-maven-plugin"

echo "═══════════════════════════════════════════════════════════"
echo "  AI Test Generator — Merging results + generating report"
echo "═══════════════════════════════════════════════════════════"

mkdir -p "$ALLURE_RESULTS"

# Merge chromium, firefox, webkit UI results + api results.
# Smoke (allure-smoke) is deliberately excluded.
for target in chromium firefox webkit api; do
    VOL="${COMPOSE_PROJECT}_allure-${target}"
    echo "→ Extracting: $VOL"
    docker run --rm \
        -v "${VOL}:/data:ro" \
        -v "$(pwd)/${ALLURE_RESULTS}:/merged" \
        eclipse-temurin:17-jdk-jammy \
        sh -c "cp -r /data/. /merged/ 2>/dev/null && echo '  ✔ ${target} results copied' || echo '  ⚠ ${target}: no results found'"
done

TOTAL=$(find "$ALLURE_RESULTS" -name "*.json" -o -name "*.xml" 2>/dev/null | wc -l | tr -d ' ')
echo ""
echo "→ Total result files merged: $TOTAL"
echo "  (24 UI × 3 browsers + 29 API × 1 = 103 unique test scenarios expected)"

echo ""
echo "→ Generating Allure HTML report..."
mvn allure:report \
    -Dallure.clean.skip=true \
    --no-transfer-progress -q

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║  Report ready: ${REPORT_DIR}/index.html"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""
echo "  Open with:  open ${REPORT_DIR}/index.html"
echo "  Trends:     open test-history/trend-dashboard.html"