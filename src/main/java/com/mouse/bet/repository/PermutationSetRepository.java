package com.mouse.bet.repository;

import com.mouse.bet.domain.entities.PermutationSet;
import com.mouse.bet.enums.PermutationSetStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PermutationSetRepository extends JpaRepository<PermutationSet, Long> {

    List<PermutationSet> findByPermStatus(PermutationSetStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select p from PermutationSet p
    where p.permStatus = 'SCHEDULED'
    and p.scheduledAt <= :now
""")
    List<PermutationSet> findReadyForExecution(@Param("now") LocalDateTime now);

    @Query("""
    select p from PermutationSet p
    where p.permStatus = 'PROCESSING'
""")
    List<PermutationSet> findStuckProcessing();

    @Query("""
    SELECT p FROM PermutationSet p
    WHERE p.permStatus IN ('PENDING', 'PROCESSING')
    ORDER BY p.scheduledExecutionTime DESC
""")
    Optional<PermutationSet> findLastScheduledActivePermSet();

    boolean existsByPermStatusIn(
            List<PermutationSetStatus> statuses
    );

    // Gets only the single most recently scheduled set
    Optional<PermutationSet> findTopByPermStatusOrderByScheduledExecutionTimeDesc(PermutationSetStatus status);




}