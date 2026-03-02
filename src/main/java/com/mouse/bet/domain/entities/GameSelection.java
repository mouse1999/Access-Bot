package com.mouse.bet.domain.entities;

import com.mouse.bet.enums.SelectionStatus;
import com.mouse.bet.exception.AlreadyStartedGameException;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "game_selections")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    private SelectionStatus selectionStatus = SelectionStatus.ACTIVE;

    @OneToMany(mappedBy = "selection",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<GameSelectionItem> items = new ArrayList<>();

    private GameSelection(List<Game> games) {
        this.createdAt = LocalDateTime.now();

        for(Game game : games) {
            try {
                this.addGame(game);
            } catch (AlreadyStartedGameException e) {
//                log something to show it skipped this game that started already
            }


        }
    }

    public static GameSelection create(List<Game> games) {
        if (games == null || games.isEmpty()) {
            throw new IllegalArgumentException("Selection must contain at least one game.");
        }
        return new GameSelection(games);
    }

    public void addGame(Game game) throws AlreadyStartedGameException {
        if (game.isStarted()) {
            throw new AlreadyStartedGameException("Cannot select a started game.");
        }

        GameSelectionItem item = GameSelectionItem.of(this, game);
        items.add(item);
    }

    public List<Game> getSelectedGames() {
        return items.stream()
                .map(GameSelectionItem::getGame)
                .toList();
    }
}
