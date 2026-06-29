package com.qa.ai.pages;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Base for all page selector registries.
 *
 * Each page subclass exposes:
 *   selectors()   — primary CSS selector map  (logical name → CSS)
 *   fallbacks()   — alternative selectors tried when primary fails
 *
 * Used by PlaywrightExecutor for self-healing and by the prompt template
 * to tell Claude which selectors are valid for each page.
 */
public abstract class BasePage {

    /** Primary selectors: logical name → CSS selector. */
    public abstract Map<String, String> selectors();

    /** Fallback selectors: CSS selector → list of alternatives (tried in order). */
    public Map<String, List<String>> fallbacks() {
        return Collections.emptyMap();
    }
}