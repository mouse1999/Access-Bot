package com.mouse.bet.controller;

import com.mouse.bet.service.BetProcessingService;
import com.mouse.bet.service.GameScraperService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scraper")
@RequiredArgsConstructor
public class GameScraperController {

    private final GameScraperService gameScraperService;
    private final BetProcessingService betProcessingService;

    @PostMapping("/scrape")
    public ResponseEntity<String> scrapeUpcomingGames() {

        // Check if there are unfinished PermSets
        if (betProcessingService.hasUnfinishedPermSets()) {

            return ResponseEntity
                    .badRequest()
                    .body("Cannot scrape new games. There are unfinished PermSets still processing.");
        }

        //  If all PermSets completed → allow scraping
        gameScraperService.runScraper();

        return ResponseEntity.ok("All previous PermSets completed. New games scraped successfully.");
    }
}

