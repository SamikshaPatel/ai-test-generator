package com.qa.ai.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Singleton that loads config.properties from the classpath once.
 * All executor/runner classes read settings from here — no magic constants in code.
 */
public class ConfigManager {

    private static final Logger log = LogManager.getLogger(ConfigManager.class);
    private static final ConfigManager INSTANCE = new ConfigManager();

    private final Properties props = new Properties();

    private ConfigManager() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) {
                props.load(is);
                log.info("Loaded config.properties ({} keys)", props.size());
            } else {
                log.warn("config.properties not found on classpath — using built-in defaults");
            }
        } catch (IOException e) {
            log.error("Failed to load config.properties", e);
        }
    }

    public static ConfigManager get() {
        return INSTANCE;
    }

    // Resolution order (12-factor): system property → env var → config.properties → default
    // This allows Docker/CI to inject settings via environment variables without
    // modifying config.properties or passing -D flags through every layer.
    // Env var lookup: tries the key as-is, then UPPER_SNAKE_CASE (e.g. "browser" → "BROWSER").

    public String getString(String key, String defaultValue) {
        String sysProp = System.getProperty(key);
        if (sysProp != null) return sysProp;
        String envVal = envVar(key);
        if (envVal != null) return envVal;
        return props.getProperty(key, defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String sysProp = System.getProperty(key);
        if (sysProp != null) return Boolean.parseBoolean(sysProp.trim());
        String envVal = envVar(key);
        if (envVal != null) return Boolean.parseBoolean(envVal.trim());
        String val = props.getProperty(key);
        return val != null ? Boolean.parseBoolean(val.trim()) : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        String sysProp = System.getProperty(key);
        String envVal  = envVar(key);
        String val = sysProp != null ? sysProp : (envVal != null ? envVal : props.getProperty(key));
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid integer for key '{}': '{}' — using default {}", key, val, defaultValue);
            return defaultValue;
        }
    }

    /** Checks env var by key as-is, then by UPPER_SNAKE_CASE conversion. */
    private static String envVar(String key) {
        String direct = System.getenv(key);
        if (direct != null) return direct;
        String upper = key.toUpperCase().replace('.', '_').replace('-', '_');
        return System.getenv(upper);
    }
}