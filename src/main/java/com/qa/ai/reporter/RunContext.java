package com.qa.ai.reporter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Immutable identity for one test run — created once per JVM.
 *
 * Provides a short run ID (8 hex chars) and ISO timestamp used across:
 *   - Agent activity report filenames
 *   - executor.json build name
 *   - Allure environment panel
 *   - Machine-readable JSON exports
 *
 * Example:  RUN-3F2A8B1C  |  2026-05-11T18:23:36Z
 */
public final class RunContext {

    private static final String RUN_ID;
    private static final String TIMESTAMP;
    private static final String TIMESTAMP_FILE_SAFE;

    static {
        RUN_ID    = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        Instant now = Instant.now();
        TIMESTAMP = DateTimeFormatter.ISO_INSTANT.format(now);
        TIMESTAMP_FILE_SAFE = DateTimeFormatter
                .ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneOffset.UTC)
                .format(now);
    }

    private RunContext() {}

    /** Short 8-char hex identifier, e.g. "3F2A8B1C". */
    public static String getRunId()            { return RUN_ID; }

    /** ISO-8601 UTC timestamp, e.g. "2026-05-11T18:23:36Z". */
    public static String getTimestamp()        { return TIMESTAMP; }

    /** File-safe timestamp, e.g. "20260511-182336". */
    public static String getTimestampSafe()    { return TIMESTAMP_FILE_SAFE; }

    /** Human-readable label used in report headers and executor.json. */
    public static String getLabel()            { return "RUN-" + RUN_ID + " @ " + TIMESTAMP; }

    /** Filename stem for run-specific artifacts, e.g. "20260511-182336-3F2A8B1C". */
    public static String getFilenameStem()     { return TIMESTAMP_FILE_SAFE + "-" + RUN_ID; }
}