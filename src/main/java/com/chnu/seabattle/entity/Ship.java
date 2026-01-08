package com.chnu.seabattle.entity;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "ships")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Ship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "match_player_id")
    private MatchPlayer matchPlayer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ShipType shipType;

    @Column(nullable = false, updatable = false)
    private int startX;

    @Column(nullable = false, updatable = false)
    private int startY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Orientation orientation;

    private int hits;

    private boolean isSunk;

}

