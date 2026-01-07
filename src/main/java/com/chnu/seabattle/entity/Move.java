package com.chnu.seabattle.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.util.UUID;

@Entity
@Table(name = "moves",
        uniqueConstraints = @UniqueConstraint(columnNames = {
                "match_id", "targetX", "targetY"
        }),
        indexes = @Index(name = "idx_shooter_id", columnList = "shooterId")
)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Move {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(nullable = false, updatable = false)
    private UUID shooterId;

    @Column(nullable = false, updatable = false)
    private int targetX;
    @Column(nullable = false, updatable = false)
    private int targetY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private MoveResult moveResult;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private java.time.Instant createdAt;
}
