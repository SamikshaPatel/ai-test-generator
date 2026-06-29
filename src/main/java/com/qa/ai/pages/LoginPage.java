package com.qa.ai.pages;

import java.util.List;
import java.util.Map;

/** Selector registry for the SauceDemo login page. */
public class LoginPage extends BasePage {

    public static final String USERNAME_INPUT = "#user-name";
    public static final String PASSWORD_INPUT = "#password";
    public static final String LOGIN_BUTTON   = "#login-button";
    public static final String ERROR_MESSAGE  = "[data-test='error1']";

    @Override
    public Map<String, String> selectors() {
        return Map.of(
            "username_input", USERNAME_INPUT,
            "password_input", PASSWORD_INPUT,
            "login_button",   LOGIN_BUTTON,
            "error_message",  ERROR_MESSAGE
        );
    }

    @Override
    public Map<String, List<String>> fallbacks() {
        return Map.of(
            LOGIN_BUTTON, List.of("[data-test='login-button']", "input[type='submit']"),
            ERROR_MESSAGE, List.of(".error-message-container", "h3[data-test='error']")
        );
    }
}