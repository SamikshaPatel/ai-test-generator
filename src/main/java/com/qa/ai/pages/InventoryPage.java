package com.qa.ai.pages;

import java.util.List;
import java.util.Map;

/** Selector registry for the SauceDemo inventory/products page. */
public class InventoryPage extends BasePage {

    public static final String PAGE_TITLE      = ".title";
    public static final String INVENTORY_LIST  = ".inventory_list";
    public static final String SORT_DROPDOWN   = ".product_sort_container";
    public static final String ITEM_NAME       = ".inventory_item_name1";
    public static final String ITEM_PRICE      = ".inventory_item_price";
    public static final String ADD_TO_CART_BTN = ".btn_primary.btn_inventory";
    public static final String CART_BADGE      = ".shopping_cart_badge";
    public static final String CART_LINK       = ".shopping_cart_link";

    @Override
    public Map<String, String> selectors() {
        return Map.of(
            "page_title",     PAGE_TITLE,
            "inventory_list", INVENTORY_LIST,
            "sort_dropdown",  SORT_DROPDOWN,
            "item_name",      ITEM_NAME,
            "item_price",     ITEM_PRICE,
            "add_to_cart",    ADD_TO_CART_BTN,
            "cart_badge",     CART_BADGE,
            "cart_link",      CART_LINK
        );
    }

    @Override
    public Map<String, List<String>> fallbacks() {
        return Map.of(
            ADD_TO_CART_BTN, List.of("[data-test='add-to-cart-sauce-labs-backpack']", "button.btn_inventory"),
            CART_BADGE,      List.of("[data-test='shopping-cart-badge']"),
            SORT_DROPDOWN,   List.of("[data-test='product-sort-container']", "select.product_sort_container"),
            ITEM_NAME,       List.of(".inventory_item_name")
        );
    }
}