package com.mouse.bet.controller;

import com.mouse.bet.domain.entities.PermGame;
import com.mouse.bet.domain.entities.PermutationSet;
import com.mouse.bet.enums.PermutationSetStatus;
import com.mouse.bet.repository.PermutationSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/permsets")
@RequiredArgsConstructor
public class PermutationSetController {

    private final PermutationSetRepository permutationSetRepository;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * GET /api/permsets
     * Returns all PermutationSets with status, games, and createdAt.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllPermSets() {
        List<PermutationSet> sets = permutationSetRepository.findAll();

        List<Map<String, Object>> response = sets.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/permsets?status=PENDING
     * Returns PermutationSets filtered by status.
     */
    @GetMapping(params = "status")
    public ResponseEntity<List<Map<String, Object>>> getPermSetsByStatus(
            @RequestParam PermutationSetStatus status) {

        List<PermutationSet> sets = permutationSetRepository.findByPermStatus(status);

        List<Map<String, Object>> response = sets.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/permsets/{id}
     * Returns a single PermutationSet by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getPermSetById(@PathVariable Long id) {
        return permutationSetRepository.findById(id)
                .map(set -> ResponseEntity.ok(toDto(set)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── DTO mapper ────────────────────────────────────────────────────────────

    private Map<String, Object> toDto(PermutationSet set) {
        List<Map<String, String>> games = set.getPermGames().stream()
                .map(this::gameToDto)
                .collect(Collectors.toList());

        return Map.of(
                "id",           set.getId(),
                "status",       set.getPermStatus().name(),
                "createdAt",    format(set.getCreatedAt()),
                "scheduledAt",  format(set.getScheduledAt()),
                "betReference", set.getBetReference() != null ? set.getBetReference() : "",
                "retryCount",   set.getRetryCount(),
                "games",        games
        );
    }

    private Map<String, String> gameToDto(PermGame permGame) {
        return Map.of(
                "homeTeam",   permGame.getGame().getHomeTeam(),
                "awayTeam",   permGame.getGame().getAwayTeam(),
                "matchCode",  permGame.getGame().getMatchCode(),
                "market",     permGame.getMarket().getDisplayName(),
                "outcome",    permGame.getOutcome().getDisplayName()
        );
    }

    private String format(LocalDateTime dt) {
        return dt != null ? dt.format(FORMATTER) : "";
    }
}