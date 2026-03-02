package com.mouse.bet.service;

import com.mouse.bet.domain.entities.PermutationSet;
import com.mouse.bet.enums.PermutationSetStatus;
import com.mouse.bet.repository.PermutationSetRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class  BetProcessingService {

    private final PermutationSetRepository permutationSetRepository;
    private final BetExecutionService betExecutionService;

    @Value("${bet.schedule.stagger-delay-mins:5}")
    private int staggerDelayMins;

    // Scheduler 1: The Manager (Checks DB for new work every 5 mins)
    private final ScheduledExecutorService managerScheduler =
            Executors.newSingleThreadScheduledExecutor();

    // Scheduler 2: The Worker (Executes the actual sequential bets)
    private final ScheduledExecutorService executionWorker =
            Executors.newSingleThreadScheduledExecutor();


    @PostConstruct
    private void init() {
        log.info("Starting Bet Manager and Worker services...");


        // Start the Manager to look for PENDING sets every 5 minutes
        managerScheduler.scheduleAtFixedRate(() -> {
            try {
                schedulePendingSets();
            } catch (Exception e) {
                log.error("Manager Error: {}", e.getMessage());
            }
        }, 0, 5, TimeUnit.MINUTES);
    }

    /**
     * Schedule all PENDING PermutationSets safely,
     * continuing from the last scheduled active PermSet.
     */
    @Transactional
    public void schedulePendingSets() {
        List<PermutationSet> pendingSets =
                permutationSetRepository.findByPermStatus(PermutationSetStatus.PENDING);

        if (pendingSets.isEmpty()) return;

        log.info("Scheduling {} PENDING sets with staggered delays...", pendingSets.size());
        staggerAndSchedule(pendingSets, calculateInitialStartTime());
    }

    @Transactional
    public void recoverStuckSets() {
        List<PermutationSet> stuckSets =
                permutationSetRepository.findByPermStatus(PermutationSetStatus.PROCESSING);

        if (stuckSets.isEmpty()) return;

        log.warn("Rescheduling {} stuck (PROCESSING) sets...", stuckSets.size());
        // Stuck sets always restart from now, ignoring any previously scheduled times
        staggerAndSchedule(stuckSets, LocalDateTime.now());
    }

    /**
     * Common logic to stagger a list of sets 5 minutes apart, starting from the given startTime.
     */
    private void staggerAndSchedule(List<PermutationSet> sets, LocalDateTime startTime) {
        LocalDateTime cursorTime = startTime;

        for (PermutationSet set : sets) {
            // Calculate minutes from 'now' to our scheduled slot
            long delayMinutes = Duration.between(LocalDateTime.now(), cursorTime).toMinutes();
            int finalDelay = (int) Math.max(delayMinutes, 0);

            scheduleExecution(set, finalDelay);

            // Advance the cursor for the NEXT item in the loop
            cursorTime = cursorTime.plusMinutes(staggerDelayMins);
        }
    }

    private LocalDateTime calculateInitialStartTime() {

        return permutationSetRepository.findTopByPermStatusOrderByScheduledExecutionTimeDesc(PermutationSetStatus.PROCESSING)
                .map(PermutationSet::getScheduledExecutionTime)
                .filter(time -> !time.isBefore(LocalDateTime.now()))
                .map(time -> time.plusMinutes(staggerDelayMins)).
                orElse(LocalDateTime.now());
    }

    /**
     * Schedules execution and marks the PermutationSet as PROCESSING.
     */
    private void scheduleExecution(PermutationSet set, int delayMinutes) {

        LocalDateTime executionTime =
                LocalDateTime.now().plusMinutes(delayMinutes);

        // Persist execution time
        set.setScheduledExecutionTime(executionTime);

        // Mark as PROCESSING immediately
        set.markProcessing();

        permutationSetRepository.save(set);

        executionWorker.schedule(() -> {
            try {
                betExecutionService.execute(set.getId());
            } catch (Exception e) {
                log.error("Failed to execute PermutationSet {}: {}",
                        set.getId(), e.getMessage());
            }
        }, delayMinutes, TimeUnit.MINUTES);
    }

    /**
     * Checks whether there are unfinished PermSets.
     */
    public boolean hasUnfinishedPermSets() {

        return permutationSetRepository
                .existsByPermStatusIn(List.of(
                        PermutationSetStatus.PENDING,
                        PermutationSetStatus.PROCESSING
                ));
    }

    /**
     * Ensures scheduler shuts down gracefully.
     */
    @PreDestroy
    public void shutdownScheduler() {

        log.info("Shutting down BetProcessingService scheduler...");

        managerScheduler.shutdown();
        executionWorker.shutdown();

        try {
            if (!managerScheduler.awaitTermination(60, TimeUnit.SECONDS) ||
                    !executionWorker.awaitTermination(60, TimeUnit.SECONDS)) {
                managerScheduler.shutdownNow();
                executionWorker.shutdown();
            }
        } catch (InterruptedException e) {
            managerScheduler.shutdownNow();
            executionWorker.shutdown();
        }
    }
}