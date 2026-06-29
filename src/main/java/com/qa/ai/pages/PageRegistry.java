package com.qa.ai.pages;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central lookup for all page selector registries.
 *
 * PlaywrightExecutor queries this for fallback selectors at runtime.
 * The prompt template can also read these to tell Claude which selectors
 * are stable and what alternatives exist.
 */
public class PageRegistry {

    private static final List<BasePage> PAGES = List.of(
        new LoginPage(),
        new InventoryPage(),
        new ProductDetailPage(),
        new BurgerMenuComponent()
    );

    /**
     * Returns a merged fallback map from all registered pages.
     * Key: primary CSS selector → Value: ordered list of fallback selectors.
     */
    public static Map<String, List<String>> allFallbacks() {
        Map<String, List<String>> merged = new HashMap<>();
        for (BasePage page : PAGES) {
            merged.putAll(page.fallbacks());
        }
        return merged;
    }

    /**
     * Returns all known primary selectors across all pages.
     * Useful for validation and prompt enrichment.
     */
    public static Map<String, String> allSelectors() {
        Map<String, String> merged = new HashMap<>();
        for (BasePage page : PAGES) {
            merged.putAll(page.selectors());
        }
        return merged;
    }
}