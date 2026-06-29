package com.qa.ai.pages;

import java.util.List;
import java.util.Map;

/** Selector registry for the SauceDemo burger/side navigation menu. */
public class BurgerMenuComponent extends BasePage {

    public static final String OPEN_BUTTON   = "#react-burger-menu-btn";
    public static final String CLOSE_BUTTON  = "#react-burger-cross-btn";
    public static final String ALL_ITEMS     = "#inventory_sidebar_link";
    public static final String ABOUT_LINK    = "#about_sidebar_link";
    public static final String LOGOUT_LINK   = "#logout_sidebar_link";
    public static final String RESET_LINK    = "#reset_sidebar_link";

    @Override
    public Map<String, String> selectors() {
        return Map.of(
            "open_button",  OPEN_BUTTON,
            "close_button", CLOSE_BUTTON,
            "all_items",    ALL_ITEMS,
            "about",        ABOUT_LINK,
            "logout",       LOGOUT_LINK,
            "reset",        RESET_LINK
        );
    }

    @Override
    public Map<String, List<String>> fallbacks() {
        return Map.of(
            OPEN_BUTTON,  List.of(".bm-burger-button button", "[aria-label='Open Menu']"),
            LOGOUT_LINK,  List.of("a[href*='logout']", ".bm-item[id*='logout']")
        );
    }
}