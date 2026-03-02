package com.mouse.bet.orchestrator;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import com.mouse.bet.config.LoginConfig;
import com.mouse.bet.domain.entities.PermGame;
import com.mouse.bet.domain.models.HeaderProfile;
import com.mouse.bet.domain.models.PlaywrightContext;
import com.mouse.bet.domain.models.UserAgentProfile;
import com.mouse.bet.domain.models.Viewport;
import com.mouse.bet.utils.PlaywrightUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * BettingExecutionEngine
 *
 * Orchestrates the full bet-placement flow:
 *  1. Launch browser & load session
 *  2. Navigate to main site → login (Page 1 selectors)
 *  3. Click menu item → wait for new tab to open
 *  4. Login on the new tab (Page 2 selectors, same credentials)
 *  5. Add each PermGame to the betslip
 *  6. Submit and capture the bet reference
 *
 * Session persistence strategy:
 *  - Context is loaded from disk at startup (storageDir/contextFile)
 *  - After every confirmed login (main page OR betting tab), context is saved
 *  - This means subsequent runs skip login entirely as long as cookies are valid
 */
@Slf4j
@Component
public class BettingExecutionEngine {

    private static final String EMOJI_BET     = "⚽";
    private static final String EMOJI_OK      = "✅";
    private static final String EMOJI_ERROR   = "❌";
    private static final String EMOJI_SUCCESS = "✅";
    private static final String EMOJI_WARNING = "⚠️";

    // ── Menu item that opens the betting tab ──────────────────────────────────
    private static final String SEL_MENU_ITEM = "a[href='/shop/sportbet-new']";

    @Value("${bet.expected.games.count:3}")
    private int expectedGamesCount;

    @Value("${bet.stake.amount:100}")
    private int stakeAmount;

    @Value("${bet.site.url:https://shop.accessbet.com/dashboard}")
    private String siteUrl;

    @Value("${bet.site.username}")
    private String username;

    @Value("${bet.site.password}")
    private String password;

    @Value("${bet.playwright.headless:true}")
    private boolean headless;

    @Value("${bet.playwright.storage-dir:./sessions}")
    private String storageDir;

    @Value("${bet.playwright.context-file:bet-context.json}")
    private String contextFile;

    @Value("${bet.playwright.nav-max-retries:3}")
    private int navMaxRetries;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Place all PermGames in a single betslip.
     *
     * @param permGames the list of PermGame to bet on
     * @return bet reference / confirmation string, or null on failure
     */
    public String placeBetslip(List<PermGame> permGames) throws InterruptedException {
        log.info("{} Placing betslip with {} PermGame(s)", EMOJI_BET, permGames.size());

        PlaywrightContext pwContext = new PlaywrightContext();

        try {
            // 1. Launch browser
            PlaywrightUtil.initialize(pwContext, headless);

            // 2. Load saved session or create fresh context
            //    If the session JSON exists on disk, cookies/localStorage are restored
            //    and both checkIfLoggedIn calls below will return true → no login needed.
            PlaywrightUtil.initializeContext(
                    pwContext, createEdgeDesktopProfile(), storageDir, contextFile);

            // 3. Navigate to main site with retry resilience
            navigateToGamesPage(pwContext, navMaxRetries);

            Page mainPage = pwContext.getPage();

            // 4. Login on the main page only if the restored session didn't work
            if (!PlaywrightUtil.checkIfLoggedIn(mainPage)) {
                log.info("Session not valid on main page — performing login...");
                PlaywrightUtil.login(mainPage, mainPageLoginConfig(), pwContext.getContext(),
                        storageDir, contextFile);

                if (!PlaywrightUtil.checkIfLoggedIn(mainPage)) {
                    log.error("{} Cannot proceed — main page login failed", EMOJI_ERROR);
                    return null;
                }
            } else {
                log.info("{} Main page session restored from disk — login skipped", EMOJI_OK);
                // Re-save to refresh cookie expiry timestamps on disk
                PlaywrightUtil.saveContext(pwContext.getContext(), storageDir, contextFile);
            }

            // 5. Human-like pause, then click the menu item that opens the betting tab
            PlaywrightUtil.sleepRandom(2_000, 5_000);
            Page bettingPage = openBettingTab(pwContext, mainPage);

            if (bettingPage == null) {
                log.error("{} Betting tab did not open", EMOJI_ERROR);
                return null;
            }

            // 6. Login on the new betting tab only if needed
            if (!PlaywrightUtil.checkIfLoggedIn(bettingPage, new String[]{
                    "div.sports-search",
                    "input.sports-search-input"
            })) {
                log.info("Session not valid on betting tab — performing login...");
                PlaywrightUtil.loginBettingPage(bettingPage, bettingPageLoginConfig(), pwContext.getContext(),
                        storageDir, contextFile);

//                if(!PlaywrightUtil.checkIfLoggedIn(bettingPage, new String[]{
//                        "div.sports-search",
//                        "input.sports-search-input"
//                })) {
//                    log.error("{} Cannot proceed — betting tab login failed", EMOJI_ERROR);
//                    return null;
//                }
            } else {
                log.info("{} Betting tab session restored from disk — login skipped", EMOJI_OK);
                // Re-save so the betting-tab cookies are also flushed to the session file
                PlaywrightUtil.saveContext(pwContext.getContext(), storageDir, contextFile);
            }

            // 7. Add each PermGame to the betslip on the betting tab
            for (PermGame game : permGames) {
                addGameToBetslip(bettingPage, game);
            }

            // 8. Submit betslip and capture reference
            String betRef = submitBetslip(bettingPage);
            log.info("{} Betslip submitted. Reference: {}", EMOJI_OK, betRef);

            Thread.sleep(10_000);
            return betRef;

        } catch (Exception e) {
            log.error("{} Betslip placement failed: {}", EMOJI_ERROR, e.getMessage());

            Thread.sleep(10_000);
            return null;
        } finally {
            PlaywrightUtil.close(pwContext);
        }
    }

    // ── Login configs — all selectors live here, not in PlaywrightUtil ────────

    /**
     * LoginConfig for the MAIN site page (standard Bootstrap form, first login).
     */
    private LoginConfig mainPageLoginConfig() {
        return LoginConfig.builder()
                .formSelector(".card-body:has(input#username)")
                .usernameSelector("input#username[name='username']")
                .passwordSelector("input#password[name='password']")
                .loginButtonSelector("button#loginbtn[name='loginbtn']")
                .loginButtonJsFallbackSelector("button#loginbtn")
                .errorMessageSelector("#response_msg")
                .username(username)
                .password(password)
                .build();
    }

    /**
     * LoginConfig for the BETTING TAB (new tab, second login form).
     */
    private LoginConfig bettingPageLoginConfig() {
        return LoginConfig.builder()
                .formSelector("div.login-panel")
                .usernameSelector("div.login-username input[type='text']")
                .passwordSelector("div.login-password input[type='password']")
                .loginButtonSelector("div.login-submit input[type='submit']")
                .loginButtonJsFallbackSelector("div.login-submit input[type='submit']")
                .errorMessageSelector("div.login-panel div.message")
                .username(username)
                .password(password)
                .build();
    }

    // ── Tab management ────────────────────────────────────────────────────────

    private Page openBettingTab(PlaywrightContext pwContext, Page mainPage) {
        try {
            log.info("Clicking menu item to open betting tab...");
            Locator menuItem = mainPage.locator(SEL_MENU_ITEM).first();
            menuItem.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(10_000));

            // Check how many pages exist before clicking
            int pagesBefore = pwContext.getContext().pages().size();
            log.info("Pages open before click: {}", pagesBefore);

            Page bettingPage = PlaywrightUtil.waitForNewTab(
                    pwContext.getContext(),
                    () -> menuItem.click(new Locator.ClickOptions().setForce(true)),
                    15_000
            );

            bettingPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
            bettingPage.waitForLoadState(LoadState.NETWORKIDLE);
            PlaywrightUtil.sleepRandom(1_000, 2_000);

            log.info("Pages open after click: {}", pwContext.getContext().pages().size());
            log.info("Betting page URL: {}", bettingPage.url());
            log.info("Main page URL: {}", mainPage.url());

            // Verify it's actually a different page
            if (bettingPage.url().equals(mainPage.url())) {
                log.warn("Betting page URL same as main page — tab did not open separately");
                // Find the correct tab by URL
                for (Page p : pwContext.getContext().pages()) {
                    log.info("Available tab: {}", p.url());
                    if (p.url().contains("sportbet-new") && p != mainPage) {
                        log.info("Found correct betting tab: {}", p.url());
                        return p;
                    }
                }
            }

            return bettingPage;

        } catch (Exception e) {
            log.error("{} Failed to open betting tab: {}", EMOJI_ERROR, e.getMessage());

            // Last resort — scan all open pages for the betting tab
            log.info("Scanning all open pages for betting tab...");
            for (Page p : pwContext.getContext().pages()) {
                log.info("Open tab: {}", p.url());
                if (p.url().contains("sportbet-new")) {
                    log.info("Found betting tab by scan: {}", p.url());
                    return p;
                }
            }

            return null;
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void navigateToGamesPage(PlaywrightContext context, int maxRetries) {
        int attempt = 0;
        boolean success = false;

        while (attempt < maxRetries && !success) {
            attempt++;
            try {
                log.info("Navigation attempt {}/{} to: {}", attempt, maxRetries, siteUrl);

                context.getPage().navigate(siteUrl, new Page.NavigateOptions()
                        .setTimeout(30_000)
                        .setWaitUntil(WaitUntilState.NETWORKIDLE));

                success = true;
                log.info("{} Navigated to games page on attempt {}", EMOJI_OK, attempt);

            } catch (TimeoutError e) {
                log.warn("Timeout on attempt {}/{}: {}", attempt, maxRetries, e.getMessage());
                handleRetryWait(attempt);
            } catch (PlaywrightException e) {
                log.error("Playwright error on attempt {}/{}: {}", attempt, maxRetries, e.getMessage());
                handleRetryWait(attempt);
            }
        }

        if (!success) {
            log.error("{} Failed to navigate to {} after {} attempts", EMOJI_ERROR, siteUrl, maxRetries);
            throw new RuntimeException("Critical navigation failure: " + siteUrl);
        }
    }

    private void handleRetryWait(int attempt) {
        try {
            Thread.sleep(attempt * 2_000L);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Betslip helpers ───────────────────────────────────────────────────────

//    private void addGameToBetslip(Page page, PermGame game) {
//        log.info("{} Adding game to betslip — match code: {}", EMOJI_BET, game.getGame().getMatchCode());
//
//        final int MAX_RETRIES = 3;
//        final int MARKET_LOAD_TIMEOUT = 50_000;
//
//        FrameLocator frame = getBettingFrameLocator(page);
//
//        // sportbet-new IS the main frame — get it directly for JS evaluation
//        Frame bettingFrame = page.frames().stream()
//                .filter(f -> f.url().contains("cashier.accessbet.com"))
//                .findFirst()
//                .orElse(page.mainFrame());
//
//        log.info("Using betting frame — URL: {}", bettingFrame.url());
//
//        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
//            try {
//                log.info("Attempt {}/{} for match code '{}'", attempt, MAX_RETRIES, game.getGame().getMatchCode());
//
//                // Use page.locator() directly — no FrameLocator needed
//                Locator matchCodeInput = bettingFrame.locator("input.match-code-input").first();
//                matchCodeInput.waitFor(new Locator.WaitForOptions()
//                        .setState(WaitForSelectorState.VISIBLE)
//                        .setTimeout(10_000));
//
//                matchCodeInput.click(new Locator.ClickOptions().setForce(true));
//                matchCodeInput.fill("");
//                PlaywrightUtil.sleepRandom(200, 400);
//
//                PlaywrightUtil.typeHumanLike(matchCodeInput, game.getGame().getMatchCode());
//                PlaywrightUtil.sleepRandom(3000, 6000);
//
//                // Dispatch Enter key events directly inside the frame's JS context
//                bettingFrame.evaluate("""
//                const input = document.querySelector('input.match-code-input');
//                if (input) {
//                    input.focus();
//                    input.dispatchEvent(new KeyboardEvent('keydown',  {key: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true}));
//                    input.dispatchEvent(new KeyboardEvent('keypress', {key: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true}));
//                    input.dispatchEvent(new KeyboardEvent('keyup',    {key: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true}));
//                } else {
//                    console.warn('match-code-input not found in frame');
//                }
//            """);
//
//                log.info("Enter key dispatched for match code '{}'", game.getGame().getMatchCode());
//
//                try {
//                    Locator marketSelectionList = bettingFrame.locator("div#id-match-market-selection-list").first();
//                    marketSelectionList.waitFor(new Locator.WaitForOptions()
//                            .setState(WaitForSelectorState.VISIBLE)
//                            .setTimeout(MARKET_LOAD_TIMEOUT));
//
//                    log.info("{} Market selection list loaded for match '{}'",
//                            EMOJI_SUCCESS, game.getGame().getMatchCode());
//
//                    PlaywrightUtil.sleepRandom(500, 1_000);
//
//                    boolean selected = selectOutcome(frame, game.getMarket().getDisplayName(),
//                            game.getOutcome().getDisplayName());
//
//                    if (!selected) {
//                        log.error("{} Could not select outcome '{}' in market '{}' for match '{}'",
//                                EMOJI_ERROR, game.getOutcome(), game.getMarket().getDisplayName(),
//                                game.getGame().getMatchCode());
//                        return;
//                    }
//
//                    PlaywrightUtil.sleepRandom(800, 1_500);
//                    return;
//
//                } catch (TimeoutError e) {
//                    log.warn("{} Market selection list not displayed within {}s for match '{}' (Attempt {}/{})",
//                            EMOJI_WARNING, MARKET_LOAD_TIMEOUT / 1000, game.getGame().getMatchCode(),
//                            attempt, MAX_RETRIES);
//
//                    if (attempt < MAX_RETRIES) {
//                        log.info("Retrying match code entry...");
//                        PlaywrightUtil.sleepRandom(1_000, 2_000);
//                    } else {
//                        log.error("{} Failed to load market selection list after {} attempts for match '{}'",
//                                EMOJI_ERROR, MAX_RETRIES, game.getGame().getMatchCode());
//                        throw new RuntimeException("Market selection list not displayed after " + MAX_RETRIES + " attempts");
//                    }
//                }
//
//            } catch (TimeoutError e) {
//                log.error("{} Timeout while processing match '{}' on attempt {}/{}: {}",
//                        EMOJI_ERROR, game.getGame().getMatchCode(), attempt, MAX_RETRIES, e.getMessage());
//                if (attempt >= MAX_RETRIES)
//                    throw new RuntimeException("Failed to add game after " + MAX_RETRIES + " attempts", e);
//
//            } catch (Exception e) {
//                log.error("{} Failed to add game '{}' to betslip on attempt {}/{}: {}",
//                        EMOJI_ERROR, game.getGame().getMatchCode(), attempt, MAX_RETRIES, e.getMessage());
//                if (attempt >= MAX_RETRIES)
//                    throw new RuntimeException("Failed to add game after " + MAX_RETRIES + " attempts", e);
//            }
//        }
//    }

    private void addGameToBetslip(Page page, PermGame game) {
        log.info("{} Adding game to betslip — match code: {}", EMOJI_BET, game.getGame().getMatchCode());

        final int MAX_RETRIES = 3;
        final int MARKET_LOAD_TIMEOUT = 50_000;

        FrameLocator frame = getBettingFrameLocator(page);

        Frame bettingFrame = page.frames().stream()
                .filter(f -> f.url().contains("cashier.accessbet.com"))
                .findFirst()
                .orElse(page.mainFrame());

        log.info("Using betting frame — URL: {}", bettingFrame.url());

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.info("Attempt {}/{} for match code '{}'", attempt, MAX_RETRIES, game.getGame().getMatchCode());

                // ── Step 1: Type match code ───────────────────────────────────
                Locator matchCodeInput = bettingFrame.locator("input.match-code-input").first();
                matchCodeInput.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(10_000));

                matchCodeInput.click();
                PlaywrightUtil.sleepRandom(200, 400);
                matchCodeInput.clear();
                PlaywrightUtil.sleepRandom(200, 400);

                String matchCode = game.getGame().getMatchCode();
                matchCodeInput.pressSequentially(matchCode,
                        new Locator.PressSequentiallyOptions().setDelay(100));

                log.info("Match code '{}' typed — waiting for cursor to move to bet code input...", matchCode);
                PlaywrightUtil.sleepRandom(500, 800);

                // ── Step 2: Click bet-code-input to ensure focus ──────────────
                Locator betCodeInput = bettingFrame.locator("input.bet-code-input").first();
                betCodeInput.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(5_000));
                betCodeInput.click();
                PlaywrightUtil.sleepRandom(300, 500);

                // ── Step 3: Press Enter on bet-code-input to trigger search ───
                betCodeInput.press("Enter");
                log.info("Enter pressed on bet-code-input for match code '{}'", matchCode);
                PlaywrightUtil.sleepRandom(1_000, 2_000);

                // ── Step 4: Check for "Match not found" error ─────────────────
                Locator messageDiv = bettingFrame.locator("div.message").first();
                try {
                    messageDiv.waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(2_000));
                    String messageText = messageDiv.innerText().trim();
                    log.warn("Message div visible: '{}'", messageText);

                    if (messageText.equalsIgnoreCase("Match not found")) {
                        log.warn("{} Match not found for code '{}' (Attempt {}/{}) — retrying in 3s...",
                                EMOJI_WARNING, matchCode, attempt, MAX_RETRIES);

                        if (attempt < MAX_RETRIES) {
                            PlaywrightUtil.sleepRandom(3_000, 5_000);
                            continue;
                        } else {
                            log.error("{} Match code '{}' returned 'Match not found' after {} attempts — giving up",
                                    EMOJI_ERROR, matchCode, MAX_RETRIES);
                            throw new RuntimeException("Match not found after " + MAX_RETRIES + " attempts: " + matchCode);
                        }
                    }
                } catch (TimeoutError ignored) {
                    // Message div not visible — no error, proceed normally
                    log.info("No error message shown — proceeding to market list");
                }

                // ── Step 5: Wait for market selection list ────────────────────
                try {
                    Locator marketSelectionList = bettingFrame.locator("div#id-match-market-selection-list").first();
                    marketSelectionList.waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(MARKET_LOAD_TIMEOUT));

                    log.info("{} Market selection list loaded for match '{}'",
                            EMOJI_SUCCESS, game.getGame().getMatchCode());

                    PlaywrightUtil.sleepRandom(500, 1_000);

                    // ── Step 6: Select outcome ────────────────────────────────
                    boolean selected = selectOutcome(frame, game.getMarket().getDisplayName(),
                            game.getOutcome().getDisplayName());

                    if (!selected) {
                        log.error("{} Could not select outcome '{}' in market '{}' for match '{}'",
                                EMOJI_ERROR, game.getOutcome(), game.getMarket().getDisplayName(),
                                game.getGame().getMatchCode());
                        return;
                    }

                    PlaywrightUtil.sleepRandom(800, 1_500);
                    return;

                } catch (TimeoutError e) {
                    log.warn("{} Market selection list not displayed within {}s for match '{}' (Attempt {}/{})",
                            EMOJI_WARNING, MARKET_LOAD_TIMEOUT / 1000, game.getGame().getMatchCode(),
                            attempt, MAX_RETRIES);

                    if (attempt < MAX_RETRIES) {
                        log.info("Retrying match code entry...");
                        PlaywrightUtil.sleepRandom(1_000, 2_000);
                    } else {
                        log.error("{} Failed to load market selection list after {} attempts for match '{}'",
                                EMOJI_ERROR, MAX_RETRIES, game.getGame().getMatchCode());
                        throw new RuntimeException("Market selection list not displayed after " + MAX_RETRIES + " attempts");
                    }
                }

            } catch (TimeoutError e) {
                log.error("{} Timeout while processing match '{}' on attempt {}/{}: {}",
                        EMOJI_ERROR, game.getGame().getMatchCode(), attempt, MAX_RETRIES, e.getMessage());
                if (attempt >= MAX_RETRIES)
                    throw new RuntimeException("Failed to add game after " + MAX_RETRIES + " attempts", e);

            } catch (RuntimeException e) {
                // Re-throw RuntimeExceptions directly (e.g. Match not found after retries)
                throw e;

            } catch (Exception e) {
                log.error("{} Failed to add game '{}' to betslip on attempt {}/{}: {}",
                        EMOJI_ERROR, game.getGame().getMatchCode(), attempt, MAX_RETRIES, e.getMessage());
                if (attempt >= MAX_RETRIES)
                    throw new RuntimeException("Failed to add game after " + MAX_RETRIES + " attempts", e);
            }
        }
    }



    private boolean selectOutcome(FrameLocator frame, String marketName, String outcome) {
        log.info("Searching for market='{}' outcome='{}'", marketName, outcome);

        String[] parts = outcome.trim().split("\\s+", 2);
        String outcomeLabel = parts[0];
        String specialLine  = parts.length > 1 ? parts[1] : null;

        Locator allMarkets = frame.locator("div.match-market-block");
        int marketCount = allMarkets.count();
        log.info("Total market blocks found on page: {}", marketCount);

        for (int i = 0; i < marketCount; i++) {
            Locator market = allMarkets.nth(i);

            String nameText = market.locator(".market-name").textContent().trim();
            log.info("Checking market block [{}]: '{}'", i, nameText);
            if (!nameText.equalsIgnoreCase(marketName.trim())) continue;

            log.info("Market '{}' found at index {}", marketName, i);

            // ── Expand market if collapsed ────────────────────────────────────
            Locator selectionsBlock = market.locator("div.match-market-block-selections").first();
            String selectionsClass = selectionsBlock.getAttribute("class");
            log.info("Market '{}' selections class: '{}'", marketName, selectionsClass);

            if (selectionsClass != null && selectionsClass.contains("closed")) {
                log.info("Market '{}' is collapsed — clicking market name to expand...", marketName);
                market.locator(".market-name").first().click();
                PlaywrightUtil.sleepRandom(500, 1_000);

                // Verify it expanded
                String updatedClass = selectionsBlock.getAttribute("class");
                log.info("Market '{}' selections class after expand click: '{}'", marketName, updatedClass);

                if (updatedClass != null && updatedClass.contains("closed")) {
                    log.warn("Market '{}' still closed after first click — trying selections block directly...", marketName);
                    selectionsBlock.click(new Locator.ClickOptions().setForce(true));
                    PlaywrightUtil.sleepRandom(500, 1_000);
                    log.info("Market '{}' selections class after second attempt: '{}'",
                            marketName, selectionsBlock.getAttribute("class"));
                } else {
                    log.info("Market '{}' expanded successfully", marketName);
                }
            } else {
                log.info("Market '{}' is already expanded", marketName);
            }

            // ── Resolve sid ───────────────────────────────────────────────────
            String sid = null;

            if (specialLine != null) {
                Locator rows = market.locator("div.match-market-block-row");
                int rowCount = rows.count();
                log.info("Market '{}' has {} row(s) — looking for special line '{}'",
                        marketName, rowCount, specialLine);

                for (int r = 0; r < rowCount; r++) {
                    Locator row = rows.nth(r);
                    String rowSpecial = row.locator("div.market-special").textContent().trim();
                    log.info("  Row [{}] special: '{}'", r, rowSpecial);
                    if (!rowSpecial.equals(specialLine)) continue;

                    log.info("Special line '{}' found at row {}", specialLine, r);
                    Locator targetSelection = row.locator("div.match-selection")
                            .filter(new Locator.FilterOptions()
                                    .setHas(frame.locator("div.match-outcome")
                                            .filter(new Locator.FilterOptions().setHasText(outcomeLabel))));

                    if (targetSelection.count() == 0) {
                        log.warn("Outcome '{}' not found in row for line '{}'", outcomeLabel, specialLine);
                        return false;
                    }
                    sid = targetSelection.first().getAttribute("sid");
                    log.info("Resolved sid='{}' for outcome='{}' in line '{}'", sid, outcomeLabel, specialLine);
                    break;
                }
            } else {
                Locator targetSelection = market.locator("div.match-selection")
                        .filter(new Locator.FilterOptions()
                                .setHas(frame.locator("div.match-outcome")
                                        .filter(new Locator.FilterOptions().setHasText(outcomeLabel))));

                log.info("Looking for outcome '{}' in market '{}' — found {} candidate(s)",
                        outcomeLabel, marketName, targetSelection.count());

                if (targetSelection.count() == 0) {
                    log.warn("Outcome '{}' not found in market '{}'", outcomeLabel, marketName);
                    return false;
                }
                sid = targetSelection.first().getAttribute("sid");
                log.info("Resolved sid='{}' for outcome='{}'", sid, outcomeLabel);
            }

            if (sid == null) {
                log.warn("sid not resolved for market='{}' outcome='{}'", marketName, outcome);
                return false;
            }

            final String finalSid = sid;
            log.info("Clicking sid={} (market='{}' outcome='{}')", finalSid, marketName, outcome);

            frame.locator("div.match-selection[sid='" + finalSid + "']").first()
                    .click(new Locator.ClickOptions().setForce(true));

            log.info("✅ Clicked outcome='{}' in market='{}'", outcome, marketName);
            return true;
        }

        log.warn("Market '{}' not found on page", marketName);
        return false;
    }



    private String submitBetslip(Page page) {
        log.info("Submitting betslip with stake amount: ₦{}", stakeAmount);
        try {
            Frame bettingFrame = page.frames().stream()
                    .filter(f -> f.url().contains("cashier.accessbet.com"))
                    .findFirst()
                    .orElse(page.mainFrame());

            log.info("Using betting frame for submission — URL: {}", bettingFrame.url());

            PlaywrightUtil.sleepRandom(500, 1_000);

            if (!validateBetslipCount(page)) {
                log.error("{} Betslip validation failed - expected {} games", EMOJI_ERROR, expectedGamesCount);
                return null;
            }

            log.info("{} Betslip validated - proceeding with submission", EMOJI_SUCCESS);
            PlaywrightUtil.sleepRandom(300, 600);

            // ── Stake amount ──────────────────────────────────────────────────────
            log.info("Locating stake input field...");
            Locator stakeInput = bettingFrame.locator("input.amount-input-type[name='totalstake']").first();
            stakeInput.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(5_000));
            log.info("Stake input found");

            stakeInput.click();
            PlaywrightUtil.sleepRandom(200, 400);

            stakeInput.selectText();
            PlaywrightUtil.sleepRandom(100, 200);
            stakeInput.press("Delete");
            PlaywrightUtil.sleepRandom(100, 200);

            stakeInput.click(new Locator.ClickOptions().setClickCount(3));
            PlaywrightUtil.sleepRandom(100, 200);
            stakeInput.press("Backspace");
            PlaywrightUtil.sleepRandom(100, 200);

            String currentValue = stakeInput.inputValue();
            log.info("Stake input value before typing: '{}'", currentValue);

            String stakeStr = String.valueOf(stakeAmount);
            stakeInput.fill(stakeStr);
            PlaywrightUtil.sleepRandom(200, 400);

            String typedValue = stakeInput.inputValue();
            log.info("Stake input value after fill: '{}' (expected: '{}')", typedValue, stakeStr);

            if (!typedValue.equals(stakeStr)) {
                log.warn("Stake value mismatch — clearing and retyping...");
                stakeInput.click(new Locator.ClickOptions().setClickCount(3));
                PlaywrightUtil.sleepRandom(100, 200);
                stakeInput.pressSequentially(stakeStr, new Locator.PressSequentiallyOptions().setDelay(150));
                PlaywrightUtil.sleepRandom(200, 400);
                log.info("Stake input value after pressSequentially: '{}'", stakeInput.inputValue());
            }

            log.info("{} Stake amount ₦{} entered successfully", EMOJI_SUCCESS, stakeAmount);
            PlaywrightUtil.sleepRandom(400, 700);

            // ── Accept odds checkbox ──────────────────────────────────────────────
            log.info("Locating accept odds checkbox...");
            Locator acceptOddsCheckbox = bettingFrame.locator("div.slip-option-accept-odds div.accept-odds-checkbox").first();
            acceptOddsCheckbox.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(5_000));

            String checkboxClass = acceptOddsCheckbox.getAttribute("class");
            log.info("Accept odds checkbox class: '{}'", checkboxClass);

            if (checkboxClass == null || !checkboxClass.contains("on")) {
                acceptOddsCheckbox.click();
                PlaywrightUtil.sleepRandom(300, 500);
                log.info("{} Odds changes accepted", EMOJI_SUCCESS);
            } else {
                log.info("Odds changes already accepted");
            }

            PlaywrightUtil.sleepRandom(400, 800);

            // ── Block native print dialog BEFORE clicking anything ────────────────
            log.info("Overriding window.print to suppress native print dialog...");
            bettingFrame.evaluate("window.print = function() { console.log('Print dialog blocked by bot'); }");
            log.info("window.print overridden successfully");

            // ── Place Bet button ──────────────────────────────────────────────────
            log.info("Locating Place Bet button...");
            Locator placeBetBtn = bettingFrame.locator("div.betslip-place-bet.footer-button").first();
            placeBetBtn.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(10_000));
            log.info("Place Bet button found — clicking...");
            placeBetBtn.click(new Locator.ClickOptions().setForce(true));
            PlaywrightUtil.sleepRandom(800, 1_500);
            log.info("Place Bet clicked - waiting for confirmation modal...");

            // ── Confirmation modal ────────────────────────────────────────────────
            log.info("Waiting for confirmation modal...");
            Locator confirmModal = bettingFrame.locator("div.yui-panel-container.yui-dialog.yui-simple-dialog").first();
            confirmModal.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(10_000));

            log.info("{} Confirmation modal displayed", EMOJI_SUCCESS);
            PlaywrightUtil.sleepRandom(500, 1_000);

            // ── Re-override print just before confirm click (extra safety) ─────────
            bettingFrame.evaluate("window.print = function() { console.log('Print dialog blocked by bot'); }");

            // ── Confirm button ────────────────────────────────────────────────────
            log.info("Locating Confirm button in modal...");
            Locator confirmButton = bettingFrame.locator("div.yui-dialog button")
                    .filter(new Locator.FilterOptions().setHasText("Confirm")).first();
            confirmButton.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(5_000));
            log.info("Confirm button found — clicking...");
            confirmButton.click();
            log.info("Confirm button clicked — print dialog suppressed, processing bet...");

            PlaywrightUtil.sleepRandom(3_000, 5_000);

            // ── Capture bet reference ─────────────────────────────────────────────
            log.info("Waiting for bet confirmation element...");
            Locator betsPlaced = bettingFrame.locator("div.bets-placed").first();
            try {
                betsPlaced.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(10_000));
                String confirmationText = betsPlaced.innerText().trim();
                log.info("{} Bet placed successfully — confirmation text: '{}'", EMOJI_SUCCESS, confirmationText);
                String betReference = extractBetReference(confirmationText);
                log.info("Bet Reference Number: {}", betReference);
                return betReference;
            } catch (Exception e) {
                log.warn("{} Bet submitted but confirmation element not found: {}", EMOJI_WARNING, e.getMessage());
                return null;
            }

        } catch (TimeoutError e) {
            log.error("{} Timeout during betslip submission: {}", EMOJI_ERROR, e.getMessage());
        } catch (Exception e) {
            log.error("{} Betslip submission error: {}", EMOJI_ERROR, e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    private String extractBetReference(String confirmationText) {
        try {
            String cleaned = confirmationText.replace("BET:", "").trim();
            String[] parts = cleaned.split("\\s+");
            if (parts.length > 0) return parts[0];
            log.warn("{} Could not extract bet reference from: {}", EMOJI_WARNING, confirmationText);
            return confirmationText;
        } catch (Exception e) {
            log.error("{} Error extracting bet reference: {}", EMOJI_ERROR, e.getMessage());
            return confirmationText;
        }
    }

    private boolean validateBetslipCount(Page page) {
        try {
            FrameLocator frame = getBettingFrameLocator(page);

            log.info("Validating betslip contains {} games...", expectedGamesCount);

            // ── Wait for betslip container ────────────────────────────────────────
            Locator betslip = frame.locator("div.betting-slip").first();
            betslip.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(5_000));

            PlaywrightUtil.sleepRandom(300, 600);

            // ── Nr-selections count ───────────────────────────────────────────────
            Locator nrSelectionsSpan = frame.locator("div.betslip-header span.nr-selections").first();
            try {
                nrSelectionsSpan.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(3_000));
            } catch (Exception e) {
                log.warn("{} Nr-selections element not visible in betslip", EMOJI_WARNING);
                return false;
            }

            String selectionsText = nrSelectionsSpan.innerText().trim();
            int actualCount = Integer.parseInt(selectionsText);

            log.info("Betslip selections count: {} (expected: {})", actualCount, expectedGamesCount);

            if (actualCount != expectedGamesCount) {
                log.error("{} Betslip count mismatch - Expected: {}, Actual: {}",
                        EMOJI_ERROR, expectedGamesCount, actualCount);
                return false;
            }

            // ── Suspended warning check ───────────────────────────────────────────
            Locator suspendedWarning = frame.locator("div.selections-suspended-warning").first();
            try {
                suspendedWarning.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(2_000));
                log.warn("{} Warning: Betslip has suspended selections", EMOJI_WARNING);
            } catch (Exception ignored) {}

            // ── Match groups cross-check ──────────────────────────────────────────
            Locator matchGroups = frame.locator("div.slip-match-group.match");
            int matchGroupsCount = matchGroups.count();
            if (matchGroupsCount != expectedGamesCount) {
                log.warn("{} Match groups count ({}) differs from nr-selections ({})",
                        EMOJI_WARNING, matchGroupsCount, actualCount);
            }

            log.info("{} Betslip validation passed - {} games confirmed", EMOJI_SUCCESS, actualCount);
            return true;

        } catch (NumberFormatException e) {
            log.error("{} Failed to parse nr-selections text: {}", EMOJI_ERROR, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("{} Betslip validation error: {}", EMOJI_ERROR, e.getMessage());
            return false;
        }
    }


    private FrameLocator getBettingFrameLocator(Page page) {
        log.info("Available frames on page:");
        for (Frame frame : page.frames()) {
            log.info("  Frame URL: {} | Name: {}", frame.url(), frame.name());
        }

        Frame bettingFrame = page.frames().stream()
                .filter(f -> f.url().contains("cashier.accessbet.com"))
                .findFirst()
                .orElse(null);

        if (bettingFrame != null) {
            log.info("Found betting frame: {}", bettingFrame.url());
            return page.frameLocator("iframe[src*='cashier.accessbet.com']");
        }

        log.warn("Could not find cashier.accessbet.com frame — falling back to first iframe");
        return page.frameLocator("iframe").first();
    }

    // ── Profile builder ───────────────────────────────────────────────────────

    public UserAgentProfile createEdgeDesktopProfile() {
        return UserAgentProfile.builder()
                .profileName("Windows Desktop Edge")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36 Edg/121.0.0.0")
                .viewport(new Viewport(1536, 864))
                .deviceScaleFactor(1.25)
                .isMobile(false)
                .headers(HeaderProfile.builder()
                        .standardHeaders(Map.of(
                                "Accept-Language", "en-US,en;q=0.9",
                                "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
                        ))
                        .clientHintsHeaders(Map.of(
                                "Sec-CH-UA", "\"Not A(Brand\";v=\"99\", \"Microsoft Edge\";v=\"121\", \"Chromium\";v=\"121\"",
                                "Sec-CH-UA-Mobile", "?0",
                                "Sec-CH-UA-Platform", "\"Windows\"",
                                "Sec-CH-UA-Full-Version-List", "\"Not A(Brand\";v=\"99.0.0.0\", \"Microsoft Edge\";v=\"121.0.2277.128\", \"Chromium\";v=\"121.0.6167.160\""
                        ))
                        .build())
                .build();
    }
}