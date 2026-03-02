package com.mouse.bet.orchestrator;

import com.mouse.bet.domain.entities.Game;
import com.mouse.bet.domain.entities.GameSelection;
import com.mouse.bet.domain.entities.PermGame;
import com.mouse.bet.domain.entities.PermutationSet;
import com.mouse.bet.enums.MarketType;
import com.mouse.bet.enums.OutcomeType;
import com.mouse.bet.service.PermGameService;
import com.mouse.bet.service.ValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PermutationOrchestrator {

    private final ValidationService validationService;
    private final PermGameService permGameService;

    public List<PermutationSet> generatePermutations(
            GameSelection selection,
            MarketType market) {

        List<Game> games = selection.getSelectedGames();
        int selectionSize = games.size();

        log.info("Generating {} permutations for {} games",
                (int) Math.pow(2, selectionSize),
                selectionSize);

        List<String> binaryMatrix = generateBinaryMatrix(selectionSize);
        List<PermutationSet> permutationSets = new ArrayList<>();

        // Dynamically select the main outcome based on the market type provided
        OutcomeType mainType = selectMainOutcome(market);

        for (String binary : binaryMatrix) {
            PermutationSet permutationSet = PermutationSet.create(selection);

            for (int i = 0; i < binary.length(); i++) {
                char bit = binary.charAt(i);
                Game game = games.get(i);

                OutcomeType outcome = (bit == '0')
                        ? mainType
                        : getOppositeOutcome(mainType);

                PermGame permGame = permGameService.createPermGame(
                        permutationSet,
                        game,
                        market,
                        outcome
                );

                permutationSet.addPermGame(permGame);
            }
            permutationSets.add(permutationSet);
        }

        validationService.validatePermutationCount(
                permutationSets,
                (int) Math.pow(2, selectionSize)
        );

        return permutationSets;
    }

    /**
     * Selects a default 'Main' outcome for a specific MarketType.
     */
    private OutcomeType selectMainOutcome(MarketType market) {
        return switch (market) {
            case OVER_UNDER -> OutcomeType.OVER_1_5;
//            case OVER_UNDER -> OutcomeType.OVER_2_5;
            case BTTS -> OutcomeType.BTTS_YES;
            // Add more cases as you expand MarketType
        };
    }

    /**
     * Logic to find the mathematical/betting opposite of the main type.
     */
    private OutcomeType getOppositeOutcome(OutcomeType outcomeType) {
        if (outcomeType == null) return null;

        return switch (outcomeType) {
            case OVER_1_5 -> OutcomeType.UNDER_1_5;
            case UNDER_1_5 -> OutcomeType.OVER_1_5;
            case BTTS_YES -> OutcomeType.BTTS_NO;
            case BTTS_NO -> OutcomeType.BTTS_YES;
            // Defaulting to an exception if an unknown type is passed to ensure data integrity
            default -> throw new IllegalArgumentException("No opposite mapping found for: " + outcomeType);
        };
    }

    private List<String> generateBinaryMatrix(int size) {
        List<String> matrix = new ArrayList<>();
        int total = 1 << size;

        for (int i = 0; i < total; i++) {
            int gray = i ^ (i >> 1);
            String binary = String.format("%" + size + "s", Integer.toBinaryString(gray))
                    .replace(' ', '0');
            matrix.add(binary);
        }
        return matrix;
    }
}