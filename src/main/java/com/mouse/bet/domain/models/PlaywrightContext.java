package com.mouse.bet.domain.models;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * PlaywrightContext
 *
 * Parent class that holds Playwright infrastructure objects.
 * These fields are passed into the static utility methods.
 */
@Slf4j
@Getter
@Data
public class PlaywrightContext {
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    private UserAgentProfile userAgentProfile;

}
