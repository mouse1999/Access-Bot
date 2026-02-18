package com.mouse.bet.repository;

import com.mouse.bet.domain.entities.Game;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findByStartTimeAfter(LocalDateTime time);

    boolean existsByHomeTeamAndAwayTeamAndStartTime(
            String homeTeam,
            String awayTeam,
            LocalDateTime startTime
    );

    List<Game> findByStartTimeBetween(
            LocalDateTime start,
            LocalDateTime end
    );

}