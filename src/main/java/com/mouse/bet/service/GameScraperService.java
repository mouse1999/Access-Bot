package com.mouse.bet.service;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitUntilState;
import com.mouse.bet.domain.entities.Game;
import com.mouse.bet.domain.models.HeaderProfile;
import com.mouse.bet.domain.models.PlaywrightContext;
import com.mouse.bet.domain.models.UserAgentProfile;
import com.mouse.bet.domain.models.Viewport;
import com.mouse.bet.utils.PlaywrightUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * GameScraperService
 *
 * Application service responsible for:
 * - Orchestrating Playwright scraping workflow
 * - Extracting games starting within next 3 hours
 * - Mapping raw browser data to Game entities
 * - Delegating persistence to GameService
 *
 * This class does NOT manage Playwright internals directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameScraperService {

    private final GameService gameService;
    private boolean headless = false;
    private static final String CONTEXT_FILE = "scraper-context.json";

    private static final String URL_SITE = "https://booking.accessbet.com/sports/today";

    @Value("${access.context.path:./playwright-context}")
    private String contextPath;

    @Value("${bet.expected.games.count:3}")
    private int expectedGamesCount;

    private static final String EMOJI_WARNING = "";

    private static final String EMOJI_ERROR = "";

    private static final String EMOJI_SUCCESS = "";




    /**
     * Main entry point for scraping process.
     *
     * Flow:
     * 1. Initialize PlaywrightContext
     * 2. Prepare browser/session
     * 3. Ensure login
     * 4. Navigate to games page
     * 5. Scrape games starting in next 3 hours
     * 6. Persist games
     * 7. Cleanup resources
     */
    public void runScraper() {
        log.info("Starting game scraping process...");
        PlaywrightContext pwContext = new PlaywrightContext();
        UserAgentProfile profile = createDesktopProfile();
        pwContext.setUserAgentProfile(profile);

        try {
            initializeInfrastructure(pwContext, profile);

            int maxAttempt = 4;
            navigateToGamesPage(pwContext, maxAttempt);

            // --- ADD THIS LINE HERE ---
            // Wait up to 15 seconds for at least one match row to be rendered
            log.info("Waiting for match rows to appear in DOM...");
            pwContext.getPage().waitForSelector("app-event-item", new Page.WaitForSelectorOptions().setTimeout(30000));
            pwContext.getPage().evaluate("window.scrollBy(0, 500)");
            Thread.sleep(1000);

            List<Game> games = scrapeGamesStartingNext3Hours(pwContext);
            gameService.saveScrapedGames(games);

        } catch (TimeoutError e) {
            log.error("Timed out waiting for match data to load. The site might be slow or selectors changed.");
        } catch (Exception ex) {
            log.error("Error during scraping process", ex);
        } finally {
            cleanup(pwContext);
        }
        log.info("Scraping process completed.");
    }

    /**
     * Initialize Playwright infrastructure.
     *
     * Pseudocode:
     * - Create Playwright instance
     * - Launch browser
     * - Create context (load storage state if exists)
     * - Create new page
     * - Set all into PlaywrightContext
     */
    private void initializeInfrastructure(PlaywrightContext context, UserAgentProfile profile) {

        PlaywrightUtil.initialize(context, headless);
        PlaywrightUtil.initializeContext(context, profile, contextPath, CONTEXT_FILE);

        log.info("Playwright infrastructure initialized.");
    }

    /**
     * Ensure the user is logged into the betting platform.
     *
     * Pseudocode:
     * - Navigate to dashboard
     * - Check if logout/profile element exists
     * - If not logged in:
     *     - Navigate to login page
     *     - Fill credentials
     *     - Submit login
     *     - Wait for successful navigation
     *     - Save storage state
     */
    private void ensureLoggedIn(PlaywrightContext context) {

        // Pseudocode using context.getPage()

        log.info("Login validation completed.");
    }

    /**
     * Navigate to games page.
     *
     * Pseudocode:
     * - page.navigate(gamesUrl)
     * - wait for load state
     */
    /**
     * Navigates to the games page with a retry mechanism for timeouts or network errors.
     * * @param context The Playwright infrastructure context
     * @param maxRetries Number of times to attempt navigation before failing
     */
    private void navigateToGamesPage(PlaywrightContext context, int maxRetries) {
        int attempt = 0;
        boolean success = false;

        while (attempt < maxRetries && !success) {
            attempt++;
            try {
                log.info("Navigation attempt {}/{} to: {}", attempt, maxRetries, URL_SITE);

                // Navigate with a specific timeout (e.g., 30 seconds)
                context.getPage().navigate(URL_SITE, new Page.NavigateOptions()
                        .setTimeout(30000)
                        .setWaitUntil(WaitUntilState.NETWORKIDLE)); // Ensures page is fully loaded

                success = true;
                log.info("Successfully navigated to games page on attempt {}.", attempt);

            } catch (TimeoutError e) {
                log.warn("Timeout occurred on attempt {}: {}", attempt, e.getMessage());
                handleRetryWait(attempt);
            } catch (PlaywrightException e) {
                log.error("Unexpected error during navigation on attempt {}: {}", attempt, e.getMessage());
                handleRetryWait(attempt);
            }
        }

        if (!success) {
            log.error("Failed to navigate to {} after {} attempts.", URL_SITE, maxRetries);
            throw new RuntimeException("Critical navigation failure: " + URL_SITE);
        }
    }

    /**
     * Helper to provide a small delay between retries to avoid spamming the site.
     */
    private void handleRetryWait(int attempt) {
        try {
            // Incremental backoff: 2s, 4s, 6s...
            Thread.sleep(attempt * 2000L);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Scrape games that start in the next 3 hours and above.
     *
     * Pseudocode:
     * - Query all game row elements
     * - For each row:
     *      - Extract start time
     *      - Compare:
     *          now <= startTime <= now + 3 hours
     *      - Extract home team
     *      - Extract away team
     *      - Build Game entity
     * - Return list
     */

    /**
     * Scrapes games starting in the next 3 hours and above from the betting platform.
     * Extracts match details including match code, teams, time, and league.
     *
     * @param context PlaywrightContext containing the page
     * @return List of eligible Game objects
     */
    private List<Game> scrapeGamesStartingNext3Hours(PlaywrightContext context) {
        List<Game> allEligibleGames = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threeHoursLater = now.plusHours(3);
        int currentYear = now.getYear();

        log.info("Scanning for games starting from {} onwards (3hrs+). Target count: {}",
                threeHoursLater, expectedGamesCount);

        try {
            // Wait for match items to load
            context.getPage().waitForSelector("app-event-item", new Page.WaitForSelectorOptions()
                    .setTimeout(10_000));

            PlaywrightUtil.sleepRandom(500, 1_000);

            List<ElementHandle> matchRows = context.getPage().querySelectorAll("app-event-item");

            log.info("Found {} match rows to process", matchRows.size());

            for (ElementHandle row : matchRows) {
                try {
                    // Extract Match Code
                    ElementHandle matchCodeElement = row.querySelector(".match-code");
                    if (matchCodeElement == null) {
                        log.debug("Skipping row - no match code element found");
                        continue;
                    }
                    String matchCode = matchCodeElement.innerText().trim();

                    // Extract Team Names
                    ElementHandle homeTeamElement = row.querySelector(".match-home-team");
                    ElementHandle awayTeamElement = row.querySelector(".match-away-team");

                    if (homeTeamElement == null || awayTeamElement == null) {
                        log.debug("Skipping match code {} - missing team elements", matchCode);
                        continue;
                    }

                    String homeTeam = homeTeamElement.innerText().trim();
                    String awayTeam = awayTeamElement.innerText().trim();

                    // Extract Date and Time
                    ElementHandle dateElement = row.querySelector(".match-date");
                    ElementHandle timeElement = row.querySelector(".match-time");

                    if (dateElement == null || timeElement == null) {
                        log.debug("Skipping match code {} - missing date/time elements", matchCode);
                        continue;
                    }

                    String dateStr = dateElement.innerText().trim(); // e.g., "27 Feb"
                    String timeStr = timeElement.innerText().trim(); // e.g., "21:00"

                    // Extract League/Tournament
                    ElementHandle leagueElement = row.querySelector(".match-tournament");
                    String league = leagueElement != null ? leagueElement.innerText().trim() : "Unknown League";

                    // Extract Sport
                    ElementHandle sportElement = row.querySelector(".match-sport");
                    String sport = sportElement != null ? sportElement.innerText().trim() : "Unknown Sport";

                    // Parse date — format: "27 Feb 21:00 2026"
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM HH:mm yyyy", Locale.ENGLISH);
                    String fullDateTimeStr = String.format("%s %s %d", dateStr, timeStr, currentYear);
                    LocalDateTime startTime = LocalDateTime.parse(fullDateTimeStr, formatter);

                    // Filter: games starting 3 hours from now or later (no upper bound)
                    if (!startTime.isBefore(threeHoursLater)) {
                        Game game = Game.create(homeTeam, awayTeam, startTime, matchCode);

                        allEligibleGames.add(game);

                        log.info("{} Found eligible game: {} vs {} ({}) at {} - Match Code: {}",
                                EMOJI_SUCCESS, homeTeam, awayTeam, league, startTime, matchCode);
                    } else {
                        log.debug("Game {} vs {} at {} starts within 3 hours — skipping",
                                homeTeam, awayTeam, startTime);
                    }

                } catch (Exception e) {
                    log.debug("Skipping non-match or malformed row: {}", e.getMessage());
                }
            }

            // ── Selection Logic ───────────────────────────────────────────────────

            if (allEligibleGames.isEmpty()) {
                log.warn("{} No eligible games found starting 3+ hours from now", EMOJI_WARNING);
                return allEligibleGames;
            }

            if (allEligibleGames.size() < expectedGamesCount) {
                log.warn("{} Only found {} eligible games (3hrs+), but {} were requested.",
                        EMOJI_WARNING, allEligibleGames.size(), expectedGamesCount);
                return allEligibleGames;
            }

            // Shuffle and pick the required amount
            List<Game> shuffled = new ArrayList<>(allEligibleGames);
            Collections.shuffle(shuffled);

            List<Game> selectedGames = shuffled.stream()
                    .limit(expectedGamesCount)
                    .toList();

            log.info("{} Successfully selected {} random games from {} eligible matches.",
                    EMOJI_SUCCESS, selectedGames.size(), allEligibleGames.size());

            selectedGames.forEach(game ->
                    log.info("Selected: {} vs {} at {} - Match Code: {}",
                            game.getHomeTeam(), game.getAwayTeam(), game.getStartTime(), game.getMatchCode())
            );

            return selectedGames;

        } catch (TimeoutError e) {
            log.error("{} Timeout waiting for match items to load: {}", EMOJI_ERROR, e.getMessage());
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("{} Error scraping games: {}", EMOJI_ERROR, e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Cleanup Playwright resources safely.
     *
     * Pseudocode:
     * - context.getPage().close()
     * - context.getContext().close()
     * - context.getBrowser().close()
     * - context.getPlaywright().close()
     */
    /**
     * Safely closes all Playwright resources in the correct order.
     * This prevents memory leaks and orphaned browser processes.
     *
     * @param context The PlaywrightContext containing resources to be closed.
     */
    private void cleanup(PlaywrightContext context) {
        log.info("Starting safe cleanup of Playwright resources...");

        if (context == null) {
            log.warn("Cleanup called with a null context. Nothing to do.");
            return;
        }

        try {
            // 1. Close the Page
            if (context.getPage() != null) {
                context.getPage().close();
                log.debug("Page closed.");
            }

            // 2. Close the Browser Context (this also saves session storage if configured)
            if (context.getContext() != null) {
                context.getContext().close();
                log.debug("Browser context closed.");
            }

            // 3. Close the Browser instance
            if (context.getBrowser() != null) {
                context.getBrowser().close();
                log.debug("Browser closed.");
            }

            // 4. Finally, close the Playwright engine
            if (context.getPlaywright() != null) {
                context.getPlaywright().close();
                log.debug("Playwright engine closed.");
            }

            log.info("Playwright resources successfully cleaned up.");

        } catch (Exception e) {
            log.error("An error occurred during Playwright cleanup: {}", e.getMessage(), e);
        }
    }



    public UserAgentProfile createDesktopProfile() {
        return UserAgentProfile.builder()
                .profileName("Windows Desktop Chrome")
                // Updated to a modern Windows 10/11 Chrome User Agent
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                // Standard Full HD Viewport
                .viewport(new Viewport(1920, 1080))
                // Standard desktop monitors use a scale factor of 1.0
                .deviceScaleFactor(1.0)
                .isMobile(false)
                .headers(HeaderProfile.builder()
                        .standardHeaders(Map.of(
                                "Accept-Language", "en-US,en;q=0.9"
//                                "Sec-Fetch-Dest", "document",
//                                "Sec-Fetch-Mode", "navigate",
//                                "Sec-Fetch-Site", "none",
//                                "Sec-Fetch-User", "?1"
                        ))
                        .clientHintsHeaders(Map.of(
                                "Sec-CH-UA", "\"Not A(Brand\";v=\"99\", \"Google Chrome\";v=\"121\", \"Chromium\";v=\"121\"",
                                "Sec-CH-UA-Mobile", "?0", // ?0 indicates Desktop
                                "Sec-CH-UA-Platform", "\"Windows\""
                        ))
                        .build())
                .build();
    }
}
