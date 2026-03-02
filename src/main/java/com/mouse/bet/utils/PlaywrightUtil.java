package com.mouse.bet.utils;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.mouse.bet.config.LoginConfig;
import com.mouse.bet.domain.models.PlaywrightContext;
import com.mouse.bet.domain.models.UserAgentProfile;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PlaywrightUtil
 *
 * Selector-agnostic utility class for Playwright operations.
 * All login selectors are supplied via LoginConfig — this class
 * contains no hardcoded form selectors so it can handle any login
 * page the engine encounters.
 */
@Slf4j
public final class PlaywrightUtil {

    private static final String EMOJI_INIT    = "🚀";
    private static final String EMOJI_BET     = "⚽";
    private static final String EMOJI_SUCCESS = "✅";
    private static final String EMOJI_ERROR   = "❌";

    private PlaywrightUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ── Browser lifecycle ─────────────────────────────────────────────────────

    /**
     * Initialize Playwright and launch Chromium with optimized performance flags.
     */
    public static void initialize(PlaywrightContext pwContext, boolean headless) {
        log.info("{} {} Initializing Optimized Playwright...", EMOJI_INIT, EMOJI_BET);
        try {
            Playwright playwright = Playwright.create();

            List<String> optimizedArgs = Arrays.asList(
                    "--start-maximized",
                    "--window-size=2560,1440",
                    "--force-device-scale-factor=1",
                    "--disable-blink-features=AutomationControlled"
            );

            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(headless)
                    .setArgs(optimizedArgs)
                    .setSlowMo(0));

            pwContext.setPlaywright(playwright);
            pwContext.setBrowser(browser);

            log.info("{} {} Playwright initialized successfully", EMOJI_SUCCESS, EMOJI_INIT);
        } catch (Exception e) {
            log.error("{} {} Failed to initialize Playwright: {}", EMOJI_ERROR, EMOJI_INIT, e.getMessage());
            throw new RuntimeException("Playwright init failed", e);
        }
    }

    /**
     * Load existing session or create a new browser context with profile headers.
     */
    public static void initializeContext(PlaywrightContext pwContext, UserAgentProfile profile,
                                         String storageDir, String contextFile) {
        Path path = Paths.get(storageDir, contextFile);
        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setUserAgent(profile.getUserAgent())
                .setLocale("en-US")
                .setIgnoreHTTPSErrors(true);

        if (Files.exists(path)) {
            log.info("Loading existing storage state from: {}", path);
            options.setStorageStatePath(path);
        }

        BrowserContext context = pwContext.getBrowser().newContext(options);
        pwContext.setContext(context);
        pwContext.setPage(context.newPage());
    }

    /**
     * Closes all Playwright resources.
     */
    public static void close(PlaywrightContext pwContext) {
        log.info("Cleaning up Playwright resources...");
        if (pwContext.getContext() != null)    pwContext.getContext().close();
        if (pwContext.getBrowser() != null)    pwContext.getBrowser().close();
        if (pwContext.getPlaywright() != null) pwContext.getPlaywright().close();
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    /**
     * Performs a human-like login on the given page using selectors and credentials
     * from the supplied LoginConfig.
     *
     * Designed to work with ANY login form — pass a different LoginConfig for each
     * distinct login page the flow encounters (e.g. main site vs. tab that opens after
     * clicking a menu item).
     *
     * @param page         the Playwright Page that shows the login form
     * @param config       all selectors and credentials for this specific login form
     * @param storageDir   directory to persist the session file
     * @param contextFile  filename for the session JSON
     * @param context      BrowserContext used to save session state after login
     */
    public static void login(Page page, LoginConfig config, BrowserContext context,
                             String storageDir, String contextFile) {

        log.info("🔐 Starting login for: {}", maskUsername(config.getUsername()));

        try {
            if (checkIfLoggedIn(page)) {
                log.info("{} Already logged in — skipping", EMOJI_SUCCESS);
                return;
            }

            // Wait for the form container — handles async / Angular rendering
            page.locator(config.getFormSelector()).waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(20_000));

            Locator usernameInput = page.locator(config.getUsernameSelector());
            Locator passwordInput = page.locator(config.getPasswordSelector());
            Locator loginButton   = page.locator(config.getLoginButtonSelector());

            // Wait for the username field to be individually interactive
            usernameInput.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(10_000));

            // Human-like: pause → click → type
            sleepRandom(600, 1_200);
            usernameInput.click();
            typeHumanLike(usernameInput, config.getUsername());

            sleepRandom(400, 800);
            passwordInput.click();
            typeHumanLike(passwordInput, config.getPassword());

            sleepRandom(2000, 3000);

            // Click submit; JS fallback if framework intercepts the event
            if (!clickButton(page, loginButton, config.getLoginButtonJsFallbackSelector())) {
                log.error("{} Login button click failed entirely", EMOJI_ERROR);
                return;
            }

            // Wait for the site to process the login response
            sleepRandom(3_000, 6_000);

            // Surface any inline error the site renders (optional selector)
            if (config.getErrorMessageSelector() != null) {
                try {
                    Locator errorMsg = page.locator(config.getErrorMessageSelector());
                    if (errorMsg.isVisible(new Locator.IsVisibleOptions().setTimeout(2_000))) {
                        String msg = errorMsg.innerText();
                        if (msg != null && !msg.isBlank()) {
                            log.warn("Site login error response: {}", msg);
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (checkIfLoggedIn(page)) {
                saveContext(context, storageDir, contextFile);
                log.info("{} Login successful — session saved", EMOJI_SUCCESS);
            } else {
                log.error("{} Login verification failed", EMOJI_ERROR);
            }

        } catch (Exception e) {
            log.error("{} Login failed: {}", EMOJI_ERROR, e.getMessage());
        }
    }

    /**
     * Waits for a new browser tab to open after performing an action (e.g. clicking
     * a menu item), then returns that tab as a Page.
     *
     * @param context        the BrowserContext that owns the tabs
     * @param triggerAction  the Runnable that triggers the new tab (e.g. menu click)
     * @param timeoutMs      how long to wait for the new tab in milliseconds
     * @return the newly opened Page, fully loaded
     */
    public static Page waitForNewTab(BrowserContext context, Runnable triggerAction, int timeoutMs) {
        log.info("Waiting for new tab to open...");
        // Playwright Java: waitForPage(callback, options) where the callback is the
        // action that triggers the new tab. The Page is returned once the tab opens.
        Page newPage = context.waitForPage(
                new BrowserContext.WaitForPageOptions().setTimeout(timeoutMs),
                triggerAction::run
        );
        newPage.waitForLoadState();
        log.info("{} New tab opened: {}", EMOJI_SUCCESS, newPage.url());
        return newPage;
    }

    /**
     * Checks for logged-in indicators on a page.
     * Override this method's indicator list by using the overload below if your
     * second login page uses different post-login markers.
     */
    public static boolean checkIfLoggedIn(Page page) {
        return checkIfLoggedIn(page, new String[]{
                "div#sidebarMenu",                  // sidebar container only present when logged in
                "a[href='/logout']",                // logout link
                "a[href='/transactions']"         // transactions nav link
        });
    }

    /**
     * Checks for logged-in state using a custom set of indicator selectors.
     * Requires at least 2 indicators visible to confirm session.
     *
     * @param page       the page to inspect
     * @param indicators CSS selectors that are only present when logged in
     */
    public static boolean checkIfLoggedIn(Page page, String[] indicators) {
        int count = 0;
        for (String selector : indicators) {
            try {
                Locator locator = page.locator(selector).first();
                locator.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(10_000));
                count++;
            } catch (Exception ignored) {}
        }
        return count >= 2;
    }


    public static void loginBettingPage(Page page, LoginConfig config, BrowserContext context,
                                        String storageDir, String contextFile) {

        log.info("🔐 Starting betting page login for: {}", maskUsername(config.getUsername()));

        try {
            // ── Locate the iframe containing the betting platform ─────────────────
            log.info("Available frames:");
            for (Frame frame : page.frames()) {
                log.info("  Frame URL: {} | Name: {}", frame.url(), frame.name());
            }

            Frame bettingFrame = page.frames().stream()
                    .filter(f -> f.url().contains("sportbet-new") || f.url().contains("shop.accessbet.com/shop/sportbet-new "))
                    .filter(f -> !f.url().equals(page.url()))
                    .findFirst()
                    .orElse(null);

            if (bettingFrame == null) {
                log.warn("No specific betting frame found — using main frame");
                bettingFrame = page.mainFrame();
            }

            log.info("Using frame: {}", bettingFrame.url());

            // ── Check if already logged in via frame ──────────────────────────────
            FrameLocator frameLocator = page.frameLocator("iframe").first();

            try {
                frameLocator.locator("div.sports-search").waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.ATTACHED)
                        .setTimeout(3_000));
                log.info("{} Already logged in on betting frame — skipping", EMOJI_SUCCESS);
                return;
            } catch (Exception ignored) {}

            // ── Screenshot before login ───────────────────────────────────────────
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get("./sessions/debug-betting-login.png"))
                    .setFullPage(true));
            log.info("Screenshot saved → ./sessions/debug-betting-login.png");

            // ── Find login panel inside iframe ────────────────────────────────────
            Locator formLocator = frameLocator.locator(config.getFormSelector()).first();
            try {
                formLocator.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.ATTACHED)
                        .setTimeout(20_000));
                log.info("{} Login panel found inside iframe", EMOJI_SUCCESS);
            } catch (Exception e) {
                log.error("{} Login panel not found inside iframe — dumping iframe elements...", EMOJI_ERROR);
                dumpFrameElements(page);
                return;
            }

            // ── Fill credentials via frameLocator ─────────────────────────────────
            Locator usernameInput = frameLocator.locator(config.getUsernameSelector()).first();
            Locator passwordInput = frameLocator.locator(config.getPasswordSelector()).first();
            Locator loginButton   = frameLocator.locator(config.getLoginButtonSelector()).first();

            sleepRandom(600, 1_200);
            usernameInput.click(new Locator.ClickOptions().setForce(true));
            typeHumanLike(usernameInput, config.getUsername());
            log.info("Username entered");

            sleepRandom(400, 800);
            passwordInput.click(new Locator.ClickOptions().setForce(true));
            typeHumanLike(passwordInput, config.getPassword());
            log.info("Password entered");

            sleepRandom(300, 700);

            try {
                loginButton.click(new Locator.ClickOptions().setForce(true).setTimeout(5_000));
                log.info("Login button clicked");
            } catch (Exception e) {
                log.warn("Primary click failed — using JS fallback");
                bettingFrame.evaluate("document.querySelector('" + config.getLoginButtonJsFallbackSelector() + "').click()");
            }

            sleepRandom(3_000, 6_000);

            // ── Verify login via frame ────────────────────────────────────────────
            try {
                frameLocator.locator("div.sports-search").waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.ATTACHED)
                        .setTimeout(10_000));
                saveContext(context, storageDir, contextFile);
                log.info("{} Betting frame login successful — session saved", EMOJI_SUCCESS);
            } catch (Exception e) {
                log.error("{} Betting frame login verification failed", EMOJI_ERROR);
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(Paths.get("./sessions/debug-betting-login-failed.png"))
                        .setFullPage(true));
                dumpFrameElements(page);
            }

        } catch (Exception e) {
            log.error("{} Betting page login failed: {}", EMOJI_ERROR, e.getMessage());
        }
    }

    /**
     * Dumps elements from all frames on the page for debugging.
     */
    private static void dumpFrameElements(Page page) {
        log.info("════════════ FRAME DUMP ════════════");
        for (Frame frame : page.frames()) {
            log.info("── Frame: {} ──", frame.url());
            try {
                List<ElementHandle> inputs = frame.querySelectorAll("input");
                log.info("  Inputs: {}", inputs.size());
                for (ElementHandle input : inputs) {
                    log.info("    <input type='{}' name='{}' id='{}' placeholder='{}'>",
                            input.getAttribute("type"),
                            input.getAttribute("name"),
                            input.getAttribute("id"),
                            input.getAttribute("placeholder"));
                }
                List<ElementHandle> divs = frame.querySelectorAll("div[class]");
                log.info("  Divs with class: {}", divs.size());
                for (ElementHandle div : divs) {
                    log.info("    <div class='{}'>", div.getAttribute("class"));
                }
            } catch (Exception e) {
                log.warn("  Could not dump frame {}: {}", frame.url(), e.getMessage());
            }
        }
        log.info("════════════ END FRAME DUMP ════════════");
    }

    /**
     * Dumps all meaningful elements on the page to the log for debugging.
     * Logs inputs, buttons, divs with IDs/classes, and all links.
     */
    private static void dumpPageElements(Page page) {
        log.info("════════════ PAGE ELEMENT DUMP ════════════");
        log.info("URL: {}", page.url());

        try {
            // All input elements
            List<ElementHandle> inputs = page.querySelectorAll("input");
            log.info("── Inputs ({}) ──", inputs.size());
            for (ElementHandle input : inputs) {
                String type  = input.getAttribute("type");
                String name  = input.getAttribute("name");
                String id    = input.getAttribute("id");
                String cls   = input.getAttribute("class");
                String ph    = input.getAttribute("placeholder");
                log.info("  <input type='{}' name='{}' id='{}' class='{}' placeholder='{}'>",
                        type, name, id, cls, ph);
            }

            // All buttons
            List<ElementHandle> buttons = page.querySelectorAll("button, input[type='submit'], input[type='button']");
            log.info("── Buttons ({}) ──", buttons.size());
            for (ElementHandle btn : buttons) {
                String tag  = (String) btn.evaluate("el => el.tagName.toLowerCase()");
                String text = btn.innerText();
                String id   = btn.getAttribute("id");
                String cls  = btn.getAttribute("class");
                log.info("  <{}> id='{}' class='{}' text='{}'", tag, id, cls, text.trim());
            }

            // Divs with id or class (top level clues)
            List<ElementHandle> divs = page.querySelectorAll("div[id], div[class]");
            log.info("── Divs with id/class ({}) ──", divs.size());
            for (ElementHandle div : divs) {
                String id  = div.getAttribute("id");
                String cls = div.getAttribute("class");
                if ((id != null && !id.isBlank()) || (cls != null && !cls.isBlank())) {
                    log.info("  <div id='{}' class='{}'>", id, cls);
                }
            }

            // All links
            List<ElementHandle> links = page.querySelectorAll("a[href]");
            log.info("── Links ({}) ──", links.size());
            for (ElementHandle link : links) {
                String href = link.getAttribute("href");
                String text = link.innerText();
                log.info("  <a href='{}'>{}</a>", href, text.trim());
            }

            // Full page HTML (truncated to 3000 chars)
            String html = (String) page.evaluate("document.body.innerHTML");
            log.info("── Body HTML (first 3000 chars) ──");
            log.info("{}", html.length() > 3000 ? html.substring(0, 3000) + "..." : html);

        } catch (Exception e) {
            log.error("Failed to dump page elements: {}", e.getMessage());
        }

        log.info("════════════ END ELEMENT DUMP ════════════");
    }

    /**
     * Persists the current browser session to disk.
     */
    public static void saveContext(BrowserContext context, String storageDir, String contextFile) {
        try {
            Path path = Paths.get(storageDir, contextFile);
            Files.createDirectories(path.getParent());
            context.storageState(new BrowserContext.StorageStateOptions().setPath(path));
            log.info("Context saved to: {}", path);
        } catch (Exception e) {
            log.error("Failed to save context: {}", e.getMessage());
        }
    }

    // ── Human-like interaction helpers (public for engine reuse) ─────────────

    /**
     * Types text character-by-character with simulated typos and varying delays.
     */
    public static void typeHumanLike(Locator locator, String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // 5% typo chance on letters
            if (ThreadLocalRandom.current().nextInt(100) < 5 && Character.isLetter(c)) {
                locator.pressSequentially(String.valueOf((char)(c + 1)),
                        new Locator.PressSequentiallyOptions().setDelay(randomDelay(50, 100)));
                sleepRandom(100, 200);
                locator.press("Backspace");
            }
            locator.pressSequentially(String.valueOf(c),
                    new Locator.PressSequentiallyOptions().setDelay(randomDelay(50, 200)));
        }
    }

    /**
     * Sleeps for a random duration between min and max milliseconds.
     */
    public static void sleepRandom(int min, int max) {
        try {
            Thread.sleep(randomDelay(min, max));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns a random int between min and max (inclusive).
     */
    public static int randomDelay(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    // ── Private helpers ───────────────────────────────────────────────────────




    /**
     * Attempts a direct Playwright click, then falls back to a JS querySelector click.
     *
     * @param page               the current page
     * @param button             the Locator for the button
     * @param jsFallbackSelector plain CSS selector safe for querySelector (no commas)
     */
    private static boolean clickButton(Page page, Locator button, String jsFallbackSelector) {
        try {
            button.click(new Locator.ClickOptions().setForce(true).setTimeout(5_000));
            return true;
        } catch (Exception e) {
            log.warn("Primary click intercepted — using JS fallback on: {}", jsFallbackSelector);
            try {
                page.evaluate("document.querySelector('" + jsFallbackSelector + "').click()");
                return true;
            } catch (Exception ex) {
                log.error("JS fallback click also failed: {}", ex.getMessage());
                return false;
            }
        }
    }

    private static String maskUsername(String u) {
        return u != null && u.length() > 3 ? u.substring(0, 3) + "***" : "***";
    }
}