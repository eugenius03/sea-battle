package com.chnu.seabattle.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "matches")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status;

    @Column(nullable = false, unique = true, updatable = false)
    private String inviteToken;

    private UUID currentPlayerTurnId;

    private UUID winnerId;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL)
    private List<MatchPlayer> players;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL)
    private List<Move> moves;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant finishedAt;
}
