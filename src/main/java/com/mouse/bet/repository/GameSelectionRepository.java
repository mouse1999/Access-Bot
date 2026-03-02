package com.mouse.bet.repository;

import com.mouse.bet.domain.entities.GameSelection;
import com.mouse.bet.enums.SelectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GameSelectionRepository extends JpaRepository<GameSelection, Long> {

    /**
     * Uses a single query to fetch the selection, all its items,
     * and the associated game data.
     */
    @Query("SELECT DISTINCT gs FROM GameSelection gs " +
            "LEFT JOIN FETCH gs.items i " +
            "LEFT JOIN FETCH i.game " +
            "WHERE gs.selectionStatus = :status")
    List<GameSelection> findBySelectionStatusWithItems(@Param("status") SelectionStatus status);
}