package com.mouse.bet.repository;

import com.mouse.bet.domain.entities.GameSelection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSelectionRepository extends JpaRepository<GameSelection, Long> {
}
