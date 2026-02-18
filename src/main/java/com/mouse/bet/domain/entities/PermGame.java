package com.mouse.bet.domain.entities;

import com.mouse.bet.enums.MarketType;
import com.mouse.bet.enums.OutcomeType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "perm_games")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PermGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permutation_set_id", nullable = false)
    private PermutationSet permutationSet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Enumerated(EnumType.STRING)
    private MarketType market;

    @Enumerated(EnumType.STRING)
    private OutcomeType outcome;

    public static PermGame create(PermutationSet set,
                                  Game game,
                                  MarketType market,
                                  OutcomeType outcome) {

        if (game == null || market == null || outcome == null) {
            throw new IllegalArgumentException("Invalid PermGame configuration");
        }

        return PermGame.builder()
                .permutationSet(set)
                .game(game)
                .market(market)
                .outcome(outcome)
                .build();
    }

    public boolean isValid() {
        return game != null && market != null && outcome != null;
    }
}

