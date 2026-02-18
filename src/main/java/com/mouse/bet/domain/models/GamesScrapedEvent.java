package com.mouse.bet.domain.models;

import com.mouse.bet.domain.entities.Game;
import lombok.Getter;

import java.util.List;

@Getter
public class GamesScrapedEvent {

    private final List<Game> games;

    public GamesScrapedEvent(List<Game> games) {
        this.games = games;
    }
}
