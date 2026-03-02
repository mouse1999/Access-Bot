package com.mouse.bet.service;

import com.mouse.bet.domain.entities.Game;
import com.mouse.bet.domain.entities.PermGame;
import com.mouse.bet.domain.entities.PermutationSet;
import com.mouse.bet.enums.MarketType;
import com.mouse.bet.enums.OutcomeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * PermGameService
 *
 * Responsible only for creating PermGame domain objects.
 * Does NOT persist them (handled by PermutationSet aggregate).
 */
@Slf4j
@Service
public class PermGameService {

    /**
     * Creates a PermGame from a Game with selected market and outcome.
     *
     * @param game     base Game entity
     * @param market   market type
     * @param outcome  selected outcome
     * @return PermGame (not persisted yet)
     */
    public PermGame createPermGame(
            PermutationSet set,
            Game game,
            MarketType market,
            OutcomeType outcome) {

        log.debug("Creating PermGame for Game {} with market {} and outcome {}",
                game.getId(), market, outcome);


        // You may copy additional data if required:
        // permGame.setHomeTeam(game.getHomeTeam());
        // permGame.setAwayTeam(game.getAwayTeam());
        // permGame.setKickoffTime(game.getKickoffTime());

        return PermGame.builder()
                .permutationSet(set)
                .game(game)
                .market(market)
                .outcome(outcome)
                .build();
    }
}
