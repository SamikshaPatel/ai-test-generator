package com.qa.ai.pages;

import java.util.List;
import java.util.Map;

/** Selector registry for the SauceDemo product detail page. */
public class ProductDetailPage extends BasePage {

    public static final String PRODUCT_NAME        = ".inventory_details_name";
    public static final String PRODUCT_DESCRIPTION = ".inventory_details_desc";
    public static final String PRODUCT_PRICE       = ".inventory_details_price1";
    public static final String ADD_TO_CART_BTN     = ".btn_primary";
    public static final String BACK_BUTTON         = "[data-test='back-to-products']";

    @Override
    public Map<String, String> selectors() {
        return Map.of(
            "product_name",        PRODUCT_NAME,
            "product_description", PRODUCT_DESCRIPTION,
            "product_price",       PRODUCT_PRICE,
            "add_to_cart",         ADD_TO_CART_BTN,
            "back_button",         BACK_BUTTON
        );
    }

    @Override
    public Map<String, List<String>> fallbacks() {
        return Map.of(
            PRODUCT_NAME,  List.of(".inventory_details_name large_size", "[data-test='inventory-item-name']"),
            PRODUCT_PRICE, List.of(".inventory_details_price", "[data-test='inventory-item-price']"),
            BACK_BUTTON,   List.of("#back-to-products", ".inventory_details_back_button")
        );
    }
}