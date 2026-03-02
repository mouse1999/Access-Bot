package com.mouse.bet.config;

import lombok.Builder;
import lombok.Getter;

/**
 * LoginConfig
 *
 * Encapsulates all selector and credential data needed to log in to a specific page.
 * Pass a different instance for each distinct login form the engine encounters.
 */
@Getter
@Builder
public class LoginConfig {

    /** CSS selector for the login form container (used as the first wait target). */
    private final String formSelector;

    /** CSS selector for the username / phone input field. */
    private final String usernameSelector;

    /** CSS selector for the password input field. */
    private final String passwordSelector;

    /** CSS selector for the submit / login button. */
    private final String loginButtonSelector;

    /**
     * CSS selector for inline error messages rendered by the site after a failed login.
     * Can be null if the site has no such element.
     */
    private final String errorMessageSelector;

    /** Plain-text fallback selector used in the JS-click fallback (no pseudo-classes). */
    private final String loginButtonJsFallbackSelector;

    /** Credentials to use for this login form. */
    private final String username;
    private final String password;
}
