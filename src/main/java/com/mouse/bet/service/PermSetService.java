package com.mouse.bet.service;

import com.mouse.bet.domain.entities.GameSelection;
import com.mouse.bet.domain.entities.PermutationSet;
import com.mouse.bet.enums.MarketType;
import com.mouse.bet.enums.SelectionStatus;
import com.mouse.bet.orchestrator.PermutationOrchestrator;
import com.mouse.bet.repository.GameSelectionRepository;
import com.mouse.bet.repository.PermutationSetRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@Service
public class PermSetService {

    private final PermutationOrchestrator permutationOrchestrator;
    private final PermutationSetRepository permutationSetRepository;
    private final GameSelectionRepository gameSelectionRepository;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    private void init() {
        log.info("Starting PermSetService background worker...");
        scheduler.scheduleAtFixedRate(() -> {
            try {
                processActiveSelections();
            } catch (Exception e) {
                log.error("Critical error in permutation scheduler: {}", e.getMessage());
            }
        }, 0, 3, TimeUnit.MINUTES);
    }

    public void processActiveSelections() {
        List<GameSelection> activeSelections = gameSelectionRepository.findBySelectionStatusWithItems(SelectionStatus.ACTIVE);

        if (activeSelections.isEmpty()) return;

        for (GameSelection selection : activeSelections) {
            try {
                // Randomly pick a MarketType for this specific selection
                MarketType randomMarket = getRandomMarketType();
                createPermSetsFromSelection(selection, randomMarket);
            } catch (Exception e) {
                log.error("Failed to process GameSelection {}: {}", selection.getId(), e.getMessage());
            }
        }
    }

    /**
     * Helper method to pick a random MarketType from the Enum values.
     */
    private MarketType getRandomMarketType() {
        MarketType[] types = MarketType.values();
        return types[ThreadLocalRandom.current().nextInt(types.length)];
    }

    @Transactional
    public void createPermSetsFromSelection(GameSelection selection, MarketType marketType) {
        log.info("Creating PermutationSets for Selection {} using Market: {}", selection.getId(), marketType);

        List<PermutationSet> permutations =
                permutationOrchestrator.generatePermutations(selection, marketType);

        permutationSetRepository.saveAll(permutations);

        selection.setSelectionStatus(SelectionStatus.NON_ACTIVE);
        gameSelectionRepository.save(selection);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }
}