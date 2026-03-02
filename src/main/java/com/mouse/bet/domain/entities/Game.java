package com.mouse.bet.domain.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "games")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String homeTeam;
    private String awayTeam;
    private LocalDateTime startTime;
    private String matchCode;

    public boolean isStarted() {
        return LocalDateTime.now().isAfter(startTime);
    }

    public static Game create(String homeTeam, String awayTeam, LocalDateTime startTime, String matchCode) {
        return Game.builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .startTime(startTime)
                .matchCode(matchCode)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Game)) return false;
        Game game = (Game) o;
        return id != null && id.equals(game.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
