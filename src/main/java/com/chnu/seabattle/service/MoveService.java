package com.chnu.seabattle.service;

import com.chnu.seabattle.entity.Move;
import com.chnu.seabattle.entity.MoveType;

import java.util.List;
import java.util.UUID;

public interface MoveService extends BaseService<Move, Long> {

    boolean existsByMatchIdAndShooterIdAndTargetXAndTargetYAndMoveType(Long matchId, UUID shooterId, Integer x, Integer y, MoveType moveType);

    List<Move> findByMatchIdAndShooterId(Long matchId, UUID shooterId);

    long countByMatchIdAndShooterIdAndMoveType(Long matchId, UUID shooterId, MoveType moveType);

    long countByMatchIdAndShooterIdAndMoveTypeAndIsMoveOriginTrue(Long matchId, UUID shooterId, MoveType moveType);
}


