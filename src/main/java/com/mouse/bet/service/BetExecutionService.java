package com.mouse.bet.service;

import com.mouse.bet.domain.entities.PermGame;
import com.mouse.bet.domain.entities.PermutationSet;
import com.mouse.bet.enums.PermutationSetStatus;
import com.mouse.bet.orchestrator.BettingExecutionEngine;
import com.mouse.bet.repository.PermutationSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * BetExecutionService
 *
 * Executes a PermutationSet by placing all its PermGame entries together in a single betslip.
 * Updates the PermutationSet aggregate directly for resumability.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BetExecutionService {

    private final PermutationSetRepository permutationSetRepository;
    private final BettingExecutionEngine bettingExecutionEngine;

    /**
     * Executes a PermutationSet as a single betslip.
     * @param setId the ID of the PermutationSet
     */
    @Transactional
    public void execute(Long setId) {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("▶ Starting execution for PermutationSet ID: {}", setId);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // ── Load PermutationSet ───────────────────────────────────────────────
        log.info("Loading PermutationSet {} from database...", setId);
        PermutationSet set = permutationSetRepository.findById(setId)
                .orElseThrow(() -> {
                    log.error("PermutationSet {} not found in database", setId);
                    return new IllegalArgumentException("PermutationSet not found: " + setId);
                });

        log.info("PermutationSet {} loaded — Status: {} | CreatedAt: {} | RetryCount: {}",
                setId, set.getPermStatus(), set.getCreatedAt(), set.getRetryCount());

        // ── Status guard ──────────────────────────────────────────────────────
        if (set.getPermStatus() != PermutationSetStatus.PROCESSING) {
            log.warn("PermutationSet {} skipped — expected PROCESSING but found: {}",
                    setId, set.getPermStatus());
            return;
        }

        log.info("PermutationSet {} is in PROCESSING state — proceeding with execution", setId);

        final int MAX_BET_RETRIES = 3;

        try {
            // ── Load games ────────────────────────────────────────────────────
            List<PermGame> permGames = set.getPermGames();
            log.info("PermutationSet {} contains {} PermGame(s):", setId, permGames.size());

            for (int i = 0; i < permGames.size(); i++) {
                PermGame g = permGames.get(i);
                log.info("  [{}] Match: {} vs {} | Code: {} | Market: {} | Outcome: {}",
                        i + 1,
                        g.getGame().getHomeTeam(),
                        g.getGame().getAwayTeam(),
                        g.getGame().getMatchCode(),
                        g.getMarket().getDisplayName(),
                        g.getOutcome().getDisplayName());
            }

            // ── Place betslip with retries ────────────────────────────────────
            log.info("Passing {} game(s) to BettingExecutionEngine for PermutationSet {}...",
                    permGames.size(), setId);

            String betReference = null;

            for (int attempt = 1; attempt <= MAX_BET_RETRIES; attempt++) {
                log.info("placeBetslip attempt {}/{} for PermutationSet {}...", attempt, MAX_BET_RETRIES, setId);

                betReference = bettingExecutionEngine.placeBetslip(permGames);

                if (betReference != null) {
                    log.info("placeBetslip succeeded on attempt {}/{} — Reference: {}",
                            attempt, MAX_BET_RETRIES, betReference);
                    break;
                }

                log.warn("placeBetslip returned null on attempt {}/{} for PermutationSet {}",
                        attempt, MAX_BET_RETRIES, setId);

                if (attempt < MAX_BET_RETRIES) {
                    long waitMs = 5_000L * attempt; // 5s, 10s backoff
                    log.info("Waiting {}ms before retry {}...", waitMs, attempt + 1);
                    Thread.sleep(waitMs);
                }
            }

            // ── Handle final result ───────────────────────────────────────────
            if (betReference == null) {
                log.error("BettingExecutionEngine returned null after {} attempts for PermutationSet {} — marking FAILED",
                        MAX_BET_RETRIES, setId);
                set.markFailed();
                permutationSetRepository.save(set);
                log.warn("PermutationSet {} saved with status: {} | RetryCount: {}",
                        setId, set.getPermStatus(), set.getRetryCount());

            } else {
                log.info("Bet placed successfully for PermutationSet {} — Reference: {}", setId, betReference);
                set.markCompleted(betReference);
                permutationSetRepository.save(set);
                log.info("PermutationSet {} saved with status: {} | BetReference: {}",
                        setId, set.getPermStatus(), set.getBetReference());
            }

            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("✅ PermutationSet {} execution complete — Ref: {}", setId, betReference);
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        } catch (Exception e) {
            log.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.error("❌ Exception during execution of PermutationSet {}: {}", setId, e.getMessage());
            log.error("Exception type: {}", e.getClass().getSimpleName());
            log.error("Stack trace: ", e);
            log.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            set.markFailed();
            permutationSetRepository.save(set);

            log.warn("PermutationSet {} marked FAILED and saved — RetryCount: {}",
                    setId, set.getRetryCount());
        }
    }
}
