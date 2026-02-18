package com.mouse.bet.repository;

import com.mouse.bet.domain.entities.PermutationSet;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PermutationSetRepository extends JpaRepository<PermutationSet, Long> {

    List<PermutationSet> findByStatus(String status);
}