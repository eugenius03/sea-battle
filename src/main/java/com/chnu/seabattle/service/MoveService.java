package com.chnu.seabattle.service;

import com.chnu.seabattle.entity.Move;

import java.util.List;
import java.util.UUID;

public interface MoveService extends BaseService<Move, Long> {

    boolean existsByMatchIdAndShooterIdAndTargetXAndTargetY(Long matchId, UUID shooterId, Integer x, Integer y);


    List<Move> findByMatchIdAndShooterId(Long matchId, UUID shooterId);
}


