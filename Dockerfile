# ─────────────────────────────────────────────────────────────────────────────
# AI Test Generator — Docker image
#
# Build:
#   docker build -t ai-test-generator .
#
# Run (single browser):
#   docker run --rm \
#     -e BROWSER=chromium \
#     -e LOGIN_PASS=secret_sauce \
#     -v "$(pwd)/target:/app/target" \
#     -v "$(pwd)/test-history:/app/test-history" \
#     ai-test-generator
#
# Multi-browser (via Compose):
#   docker-compose up --build
#
# Secrets via .env file (never commit .env):
#   echo "LOGIN_PASS=yourpass" >> .env
#   echo "ANTHROPIC_API_KEY=sk-..." >> .env
# ─────────────────────────────────────────────────────────────────────────────

# linux/amd64 is required because Playwright does not publish Firefox or WebKit
# binaries for linux/arm64.  On Apple Silicon (M1/M2/M3) Docker runs this image
# under Rosetta emulation.  Chromium-only setups can remove this override.
FROM --platform=linux/amd64 eclipse-temurin:17-jdk-jammy

ARG MAVEN_VERSION=3.9.6

# ── System packages: Maven download tool + Playwright browser dependencies ──
RUN apt-get update && apt-get install -y --no-install-recommends \
        wget curl ca-certificates fonts-liberation \
        # Chromium / shared
        libglib2.0-0 libnss3 libnspr4 libatk1.0-0 libatk-bridge2.0-0 \
        libcups2 libdrm2 libdbus-1-3 libxcb1 libxkbcommon0 libx11-6 \
        libxcomposite1 libxdamage1 libxext6 libxfixes3 libxrandr2 \
        libgbm1 libpango-1.0-0 libcairo2 libasound2 libxshmfence1 \
        libx11-xcb1 libxcursor1 libxi6 libxtst6 \
        # Firefox — GTK3 and friends (missing from original list)
        libdbus-glib-1-2 libxt6 \
        libgtk-3-0 libpangocairo-1.0-0 libcairo-gobject2 libgdk-pixbuf-2.0-0 \
        # WebKit
        libwoff1 libvpx7 libopus0 libwebpdemux2 \
        libharfbuzz-icu0 libenchant-2-2 libsecret-1-0 libhyphen0 \
        libmanette-0.2-0 libgstreamer1.0-0 gstreamer1.0-plugins-base \
        gstreamer1.0-libav gstreamer1.0-plugins-bad \
    && rm -rf /var/lib/apt/lists/*

# ── Maven ──────────────────────────────────────────────────────────────────
RUN wget -q \
      "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
    && tar -xzf "apache-maven-${MAVEN_VERSION}-bin.tar.gz" -C /opt \
    && ln -s "/opt/apache-maven-${MAVEN_VERSION}/bin/mvn" /usr/local/bin/mvn \
    && rm "apache-maven-${MAVEN_VERSION}-bin.tar.gz"

WORKDIR /app

# ── Maven dependency cache ─────────────────────────────────────────────────
# Copy only pom.xml first — this layer is invalidated only when pom.xml changes,
# not on every source change (keeps rebuild times fast).
COPY pom.xml .
RUN mvn dependency:go-offline --no-transfer-progress -q

# ── Playwright browser binaries ────────────────────────────────────────────
# Installed into /root/.cache/ms-playwright/ — cached as its own layer.
# Re-runs only when pom.xml (and therefore playwright version) changes.
RUN mvn exec:java \
      -Dexec.mainClass="com.microsoft.playwright.CLI" \
      -Dexec.args="install chromium firefox webkit" \
      --no-transfer-progress -q

# install-deps installs any remaining OS packages Playwright detects as missing.
# Runs after browser install so it knows exactly what each browser needs.
# Requires apt lists — apt-get update is re-run here before cleaning again.
RUN apt-get update && \
    mvn exec:java \
      -Dexec.mainClass="com.microsoft.playwright.CLI" \
      -Dexec.args="install-deps chromium firefox webkit" \
      --no-transfer-progress -q && \
    rm -rf /var/lib/apt/lists/*

# ── Application source ─────────────────────────────────────────────────────
COPY src ./src

# ── Entrypoint ─────────────────────────────────────────────────────────────
COPY docker/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# ── Default environment (override via -e or docker-compose environment:) ───
# Supports both UPPER_SNAKE_CASE (Docker convention) and lowercase (Maven -D convention).
ENV BROWSER=chromium
ENV HEADLESS=true

# Prevent Playwright from re-downloading browser binaries at container startup.
# Browsers are pre-installed during the docker build step above; the runtime
# Playwright.create() call still runs "playwright install <browser>" to verify,
# but with this flag set that verification is a fast no-op (no network access).
# Without this, concurrent container starts under Rosetta x86 emulation can
# cause WebKit's verification to exceed the 10-minute SDK timeout.
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

# Declare volume mount points (mount at runtime for result persistence)
VOLUME ["/app/target", "/app/test-history"]

ENTRYPOINT ["/entrypoint.sh"]