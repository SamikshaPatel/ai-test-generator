#!/bin/sh
# ─────────────────────────────────────────────────────────────────────────────
# Docker entrypoint — bridges Docker environment variables to Maven -D system
# properties so ConfigManager and AllureReporter pick them up correctly.
#
# Supports UPPER_SNAKE_CASE (Docker/CI convention) with lowercase fallback:
#   BROWSER=firefox   OR   browser=firefox
# ─────────────────────────────────────────────────────────────────────────────
set -e

# Resolve browser: BROWSER (Docker) > browser (legacy) > chromium (default)
RESOLVED_BROWSER="${BROWSER:-${browser:-chromium}}"

# Resolve headless: HEADLESS > headless > true (always true in containers)
RESOLVED_HEADLESS="${HEADLESS:-${headless:-true}}"

# Resolve timeout: TIMEOUT_MS > timeout.ms > 30000 (generous default for Rosetta/emulation)
RESOLVED_TIMEOUT="${TIMEOUT_MS:-${timeout_ms:-30000}}"

# Resolve retry max: RETRY_MAX > retry.max > 0 (no retries in Docker — not flaky, env-related)
RESOLVED_RETRY="${RETRY_MAX:-${retry_max:-0}}"

# Resolve run mode flags
RESOLVED_SMOKE="${SMOKE_ONLY:-${smoke_only:-false}}"
RESOLVED_UI_ONLY="${UI_ONLY:-${ui_only:-false}}"
RESOLVED_API_ONLY="${API_ONLY:-${api_only:-false}}"

# Select suite file — priority: smoke > ui-only > api-only > full
if [ "${RESOLVED_SMOKE}" = "true" ]; then
  RESOLVED_SUITE="src/test/resources/testng-smoke-docker.xml"
elif [ "${RESOLVED_UI_ONLY}" = "true" ]; then
  RESOLVED_SUITE="src/test/resources/testng-ui-docker.xml"
elif [ "${RESOLVED_API_ONLY}" = "true" ]; then
  RESOLVED_SUITE="src/test/resources/testng-api-docker.xml"
else
  RESOLVED_SUITE="src/test/resources/testng-docker.xml"
fi

echo "╔══════════════════════════════════════════════╗"
echo "║  AI Test Generator — Docker Runner           ║"
echo "╠══════════════════════════════════════════════╣"
echo "║  Browser  : ${RESOLVED_BROWSER}"
echo "║  Headless : ${RESOLVED_HEADLESS}"
echo "║  Timeout  : ${RESOLVED_TIMEOUT}ms"
echo "║  Retries  : ${RESOLVED_RETRY}"
echo "║  Smoke    : ${RESOLVED_SMOKE}"
echo "║  UI only  : ${RESOLVED_UI_ONLY}"
echo "║  API only : ${RESOLVED_API_ONLY}"
echo "║  Suite    : ${RESOLVED_SUITE}"
echo "╚══════════════════════════════════════════════╝"

# ── DNS readiness probe ────────────────────────────────────────────────────
# Docker Desktop on macOS (Hypervisor bridge) and Compose parallel starts can
# leave the container network unready for several seconds.  The JVM caches
# failed DNS lookups for 10 s by default (sun.net.inetaddr.negative.ttl).
# We resolve both test targets before Maven starts; on failure we retry for
# up to 30 s so all three browser containers have a clean DNS state.
dns_wait() {
  local host="$1"
  local i=0
  while [ $i -lt 30 ]; do
    if getent hosts "$host" >/dev/null 2>&1; then
      echo "DNS OK : $host"
      return 0
    fi
    i=$((i + 1))
    sleep 1
  done
  echo "WARNING: DNS still unresolved for $host after 30 s — proceeding anyway"
}
dns_wait "www.saucedemo.com"
dns_wait "jsonplaceholder.typicode.com"

# ── Clean allure-results contents before the run ───────────────────────────
# Cannot use maven-clean-plugin here because allure-results is a Docker volume
# mount point — the JVM cannot rmdir it. We delete the contents instead and pass
# -Dallure.clean.skip=true so Maven does not attempt the rmdir.
rm -rf /app/target/allure-results/* /app/target/allure-results/.* 2>/dev/null || true

exec mvn test \
  -Dbrowser="${RESOLVED_BROWSER}" \
  -Dheadless="${RESOLVED_HEADLESS}" \
  -Dtimeout.ms="${RESOLVED_TIMEOUT}" \
  -Dretry.max="${RESOLVED_RETRY}" \
  -Dsmoke.only="${RESOLVED_SMOKE}" \
  -Dallure.clean.skip=true \
  -Dsun.net.inetaddr.negative.ttl=0 \
  -Dtestng.suite.file="${RESOLVED_SUITE}" \
  --no-transfer-progress