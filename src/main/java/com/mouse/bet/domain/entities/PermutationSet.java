package com.mouse.bet.domain.entities;


import com.mouse.bet.enums.PermutationSetStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "permutation_sets")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PermutationSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selection_id", nullable = false)
    private GameSelection selection;



    @Enumerated(EnumType.STRING)
    private PermutationSetStatus permStatus;

    private LocalDateTime scheduledAt;

    private int retryCount;

    private String betReference;

    @Version
    private Long version;


    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "permutationSet",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<PermGame> permGames = new ArrayList<>();

    private LocalDateTime scheduledExecutionTime;


    private PermutationSet(GameSelection selection) {
        this.selection = selection;
        this.permStatus = PermutationSetStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public static PermutationSet create(GameSelection selection) {
        return new PermutationSet(selection);
    }

    public void addPermGame(PermGame permGame) {
        permGames.add(permGame);
    }



    public List<PermGame> getPermGames() {
        return Collections.unmodifiableList(permGames);
    }

    public boolean isReadyForExecution() {
        return permStatus == PermutationSetStatus.SCHEDULED
                && scheduledAt != null
                && scheduledAt.isBefore(LocalDateTime.now());
    }

    public void markProcessing() {
        this.permStatus = PermutationSetStatus.PROCESSING;
    }

    public void markCompleted(String betReference) {
        this.permStatus = PermutationSetStatus.COMPLETED;
        this.betReference = betReference;
    }

    public void markFailed() {
        this.permStatus = PermutationSetStatus.FAILED;
        this.retryCount++;
    }

    public void rescheduleAfterMinutes(int minutes) {
        this.permStatus = PermutationSetStatus.SCHEDULED;
        this.scheduledAt = LocalDateTime.now().plusMinutes(minutes);
    }

}

