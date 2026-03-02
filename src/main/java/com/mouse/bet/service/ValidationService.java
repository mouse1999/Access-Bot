package com.mouse.bet.service;

import com.mouse.bet.domain.entities.Game;
import com.mouse.bet.domain.entities.GameSelection;
import com.mouse.bet.enums.PermutationSetStatus;
import com.mouse.bet.repository.GameSelectionRepository;
import com.mouse.bet.repository.PermutationSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Service
public class ValidationService {

    private final PermutationSetRepository permutationSetRepository;
    private final GameSelectionRepository gameSelectionRepository;

//    /**
//     * Validates that no active permutation set is currently running.
//     */
//    public void validateNoActivePermutationSet() {
//
//        boolean exists = permutationSetRepository.existsByStatusIn(
//                List.of(
//                        PermutationSetStatus.PENDING,
//                        PermutationSetStatus.PROCESSING
//                )
//        );
//
//        if (exists) {
//            log.warn("Validation failed: Active PermutationSet exists.");
//            throw new IllegalStateException(
//                    "There is already a PermutationSet being processed."
//            );
//        }
//    }
//
//    /**
//     * Validates selected games before permutation generation.
//     */
//    public void validateGameSelections(List<GameSelection> selections) {
//
//        if (selections == null || selections.isEmpty()) {
//            throw new IllegalArgumentException("No games selected.");
//        }
//
//        validateDuplicateGames(selections);
//        validateGamesNotStarted(selections);
//    }
//
//    /**
//     * Ensures no duplicate games were selected.
//     */
//    private void validateDuplicateGames(List<GameSelection> selections) {
//
//        Set<Long> uniqueGameIds = new HashSet<>();
//
//        for (GameSelection selection : selections) {
//            Game game = selection.getSelectedGames();
//
//            if (!uniqueGameIds.add(game.getId())) {
//                throw new IllegalArgumentException(
//                        "Duplicate game selection detected for game ID: " + game.getId()
//                );
//            }
//        }
//    }
//
//    /**
//     * Ensures all games are scheduled in the future.
//     */
//    private void validateGamesNotStarted(List<GameSelection> selections) {
//
//        LocalDateTime now = LocalDateTime.now();
//
//        for (GameSelection selection : selections) {
//
//            Game game = selection.getGame();
//
//            if (game.getKickoffTime().isBefore(now)) {
//                throw new IllegalArgumentException(
//                        "Game already started: " + game.getId()
//                );
//            }
//        }
//    }

    /**
     * Validates scheduling time against last active PermutationSet
     * to prevent time collision.
     */
    public void validateSchedulingTime(LocalDateTime proposedExecutionTime) {

        permutationSetRepository
                .findLastScheduledActivePermSet()
                .ifPresent(last -> {

                    if (!proposedExecutionTime.isAfter(
                            last.getScheduledExecutionTime()
                    )) {

                        throw new IllegalStateException(
                                "Proposed execution time conflicts with existing scheduled PermutationSet."
                        );
                    }
                });
    }


    /**
     * Validates that the number of generated permutation sets
     * matches the expected mathematical count (2^n).
     *
     * @param permutationSets generated sets
     * @param expectedCount expected number of permutations
     */
    public void validatePermutationCount(
            List<?> permutationSets,
            int expectedCount) {

        if (permutationSets == null) {
            throw new IllegalArgumentException("Permutation list is null.");
        }

        int actualCount = permutationSets.size();

        if (actualCount != expectedCount) {

            log.error("Permutation count mismatch. Expected: {}, Actual: {}",
                    expectedCount,
                    actualCount
            );

            throw new IllegalStateException(
                    String.format(
                            "Invalid permutation count. Expected %d but got %d",
                            expectedCount,
                            actualCount
                    )
            );
        }

        log.info("Permutation count validated successfully: {}", actualCount);
    }

}
