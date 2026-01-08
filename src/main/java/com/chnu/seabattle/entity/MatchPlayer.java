package com.chnu.seabattle.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "match_players")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MatchPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    private UUID userId;
    private UUID guestId;

    @Column(nullable = false)
    private String reconnectToken;

    private boolean isReady;

    @OneToMany(mappedBy = "matchPlayer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Ship> ships;

    private Instant lastSeenAt;

    @Column(nullable = false)
    private boolean connected;

}
