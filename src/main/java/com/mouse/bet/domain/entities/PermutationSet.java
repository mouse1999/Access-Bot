package com.mouse.bet.domain.entities;


import com.mouse.bet.enums.BetStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "permutation_sets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PermutationSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selection_id", nullable = false)
    private GameSelection selection;

    @Enumerated(EnumType.STRING)
    private BetStatus status;

    private String betReference;

    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "permutationSet",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<PermGame> permGames = new ArrayList<>();

    private PermutationSet(GameSelection selection) {
        this.selection = selection;
        this.status = BetStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public static PermutationSet create(GameSelection selection) {
        return new PermutationSet(selection);
    }

    public void addPermGame(PermGame permGame) {
        permGames.add(permGame);
    }

    public void markProcessing() {
        if (status != BetStatus.PENDING) {
            throw new IllegalStateException("Only PENDING set can move to PROCESSING");
        }
        this.status = BetStatus.PROCESSING;
    }

    public void markCompleted(String ref) {
        if (status != BetStatus.PROCESSING) {
            throw new IllegalStateException("Must be PROCESSING to complete");
        }
        this.betReference = ref;
        this.status = BetStatus.COMPLETED;
    }

    public void markFailed() {
        this.status = BetStatus.FAILED;
    }

    public List<PermGame> getPermGames() {
        return Collections.unmodifiableList(permGames);
    }
}

