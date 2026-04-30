package com.chnu.seabattle.repository;

import com.chnu.seabattle.entity.Move;
import com.chnu.seabattle.entity.MoveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MoveRepository extends JpaRepository<Move, Long> {

    boolean existsByMatchIdAndShooterIdAndTargetXAndTargetYAndMoveType(Long matchId, UUID shooterId, Integer x, Integer y, MoveType moveType);

    List<Move> findByMatchIdAndShooterId(Long matchId, UUID shooterId);

    long countByMatchIdAndShooterIdAndMoveType(Long matchId, UUID shooterId, MoveType moveType);

    long countByMatchIdAndShooterIdAndMoveTypeAndIsMoveOriginTrue(Long matchId, UUID shooterId, MoveType moveType);
}
