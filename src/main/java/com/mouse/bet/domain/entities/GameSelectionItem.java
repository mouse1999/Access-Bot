package com.mouse.bet.domain.entities;



import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "game_selection_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GameSelectionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selection_id", nullable = false)
    private GameSelection selection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    public static GameSelectionItem of(GameSelection selection, Game game) {
        return GameSelectionItem.builder()
                .selection(selection)
                .game(game)
                .build();
    }
}

