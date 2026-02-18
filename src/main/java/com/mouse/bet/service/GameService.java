package com.mouse.bet.service;


import com.mouse.bet.domain.entities.Game;

import com.mouse.bet.domain.models.GamesScrapedEvent;
import com.mouse.bet.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * GameService
 *
 * Application service responsible for:
 * - Persisting Game aggregates
 * - Preventing duplicate games
 * - Publishing GamesScrapedEvent
 * - Exposing query operations
 *
 * This service acts as the entry point for game persistence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GameService {

    private final GameRepository gameRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Save scraped games.
     *
     * Flow:
     * 1. Filter out duplicates
     * 2. Persist only new games
     * 3. Publish GamesScrapedEvent
     *
     * @param scrapedGames list of scraped Game entities
     */
    public void saveScrapedGames(List<Game> scrapedGames) {

        if (scrapedGames == null || scrapedGames.isEmpty()) {
            log.warn("No scraped games to save.");
            return;
        }

        List<Game> newGames = new ArrayList<>();

        for (Game scraped : scrapedGames) {

            boolean exists = gameRepository
                    .existsByHomeTeamAndAwayTeamAndStartTime(
                            scraped.getHomeTeam(),
                            scraped.getAwayTeam(),
                            scraped.getStartTime()
                    );

            if (!exists) {
                newGames.add(scraped);
            }
        }

        if (newGames.isEmpty()) {
            log.info("No new games found to persist.");
            return;
        }

        List<Game> savedGames = gameRepository.saveAll(newGames);

        log.info("{} new games saved successfully.", savedGames.size());

        // Publish event for downstream processing (SelectionService)
        eventPublisher.publishEvent(new GamesScrapedEvent(savedGames));
    }

    /**
     * Retrieve a Game by ID.
     *
     * @param id Game ID
     * @return Game entity
     */
    @Transactional(readOnly = true)
    public Game getGameById(Long id) {
        return gameRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Game not found with id: " + id));
    }

    /**
     * Retrieve all available games.
     *
     * @return list of Game entities
     */
    @Transactional(readOnly = true)
    public List<Game> getAvailableGames() {
        return gameRepository.findAll();
    }

    /**
     * Retrieve games starting within next X hours.
     *
     * @param hours number of hours ahead
     * @return list of upcoming games
     */
    @Transactional(readOnly = true)
    public List<Game> getGamesStartingWithin(int hours) {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime future = now.plusHours(hours);

        return gameRepository.findByStartTimeBetween(now, future);
    }
}

