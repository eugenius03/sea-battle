package com.chnu.seabattle.repository;

import com.chnu.seabattle.dto.move.ShootingStatsProjection;
import com.chnu.seabattle.entity.Move;
import com.chnu.seabattle.entity.MoveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MoveRepository extends JpaRepository<Move, Long> {

    boolean existsByMatchIdAndShooterIdAndTargetXAndTargetYAndMoveType(Long matchId, UUID shooterId, Integer x, Integer y, MoveType moveType);

    List<Move> findByMatchIdAndShooterId(Long matchId, UUID shooterId);

    long countByMatchIdAndShooterIdAndMoveType(Long matchId, UUID shooterId, MoveType moveType);

    long countByMatchIdAndShooterIdAndMoveTypeAndIsMoveOriginTrue(Long matchId, UUID shooterId, MoveType moveType);

    @Query(value = """
            SELECT\s
            COUNT(*) AS shotsFired,
            COUNT(*) FILTER (WHERE m.move_result IN ('HIT', 'SUNK', 'FINISHED')) AS hits,
            COUNT(*) FILTER (WHERE m.move_result = 'MISS') AS misses
            FROM moves m
            JOIN match_players mp ON m.shooter_id = mp.id
            WHERE mp.user_id = :userId
              AND m.move_type != 'SURVEILLANCE_DRONE'
              AND m.is_move_origin = true
            """, nativeQuery = true)
    ShootingStatsProjection getShootingStats(UUID userId);
}
