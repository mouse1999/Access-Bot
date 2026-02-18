package com.mouse.bet.service;

import com.mouse.bet.domain.entities.Game;
import com.mouse.bet.domain.entities.GameSelection;
import com.mouse.bet.domain.entities.GameSelectionItem;
import com.mouse.bet.domain.models.GamesScrapedEvent;
import com.mouse.bet.repository.GameRepository;
import com.mouse.bet.repository.GameSelectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SelectionService
 *
 * Application Service responsible for:
 * - Creating GameSelection aggregates
 * - Validating selection business rules
 * - Reacting to GamesScrapedEvent
 *
 * This class orchestrates domain objects.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SelectionService {

    private final GameRepository gameRepository;
    private final GameSelectionRepository selectionRepository;

    /**
     * Event listener triggered after games are scraped.
     *
     * Flow:
     * 1. Extract game IDs
     * 2. Create selection
     * 3. Persist selection
     */
    @EventListener
    public void onGamesScraped(GamesScrapedEvent event) {

        List<Long> gameIds = event.getGames()
                .stream()
                .map(Game::getId)
                .collect(Collectors.toList());

        if (gameIds.size() < 5) {
            log.warn("Not enough games to create selection.");
            return;
        }

        GameSelection selection = createSelection(gameIds.subList(0, 5));

        selectionRepository.save(selection);

        log.info("GameSelection created successfully via event.");
    }

    /**
     * Create a GameSelection aggregate from list of game IDs.
     *
     * @param gameIds List of Game IDs
     * @return GameSelection aggregate
     */
    public GameSelection createSelection(List<Long> gameIds) {

        log.info("Creating selection for gameIds: {}", gameIds);

        // Load games from DB
        List<Game> games = gameRepository.findAllById(gameIds);

        if (games.size() != gameIds.size()) {
            throw new IllegalArgumentException("Some games not found.");
        }

        // Create aggregate root
        GameSelection selection = GameSelection.create(games);



        // Validate business rules
        validateSelection(selection);

        return selection;
    }

    /**
     * Validate selection business rules.
     *
     * Example rules:
     * - Must contain exactly 5 games
     * - No duplicate games
     */
    public void validateSelection(GameSelection selection) {

        if (selection.getItems().size() != 5) {
            throw new IllegalStateException("Selection must contain exactly 5 games.");
        }

        long distinctGames = selection.getItems()
                .stream()
                .map(item -> item.getGame().getId())
                .distinct()
                .count();

        if (distinctGames != 5) {
            throw new IllegalStateException("Duplicate games are not allowed.");
        }

        log.info("Selection validated successfully.");
    }
}
