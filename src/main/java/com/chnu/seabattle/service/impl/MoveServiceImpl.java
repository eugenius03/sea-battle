package com.chnu.seabattle.service.impl;

import com.chnu.seabattle.entity.Move;
import com.chnu.seabattle.entity.MoveType;
import com.chnu.seabattle.repository.MoveRepository;
import com.chnu.seabattle.service.AbstractBaseService;
import com.chnu.seabattle.service.MoveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MoveServiceImpl extends AbstractBaseService<Move, Long> implements MoveService {

    private final MoveRepository moveRepository;

    @Override
    protected MoveRepository getRepository() {
        return moveRepository;
    }

    @Override
    public boolean existsByMatchIdAndShooterIdAndTargetXAndTargetYAndMoveType(Long matchId, UUID shooterId, Integer x, Integer y, MoveType moveType) {
        return moveRepository.existsByMatchIdAndShooterIdAndTargetXAndTargetYAndMoveType(matchId, shooterId, x, y, moveType);
    }

    @Override
    public List<Move> findByMatchIdAndShooterId(Long matchId, UUID shooterId) {
        return moveRepository.findByMatchIdAndShooterId(matchId, shooterId);
    }

    @Override
    public long countByMatchIdAndShooterIdAndMoveType(Long matchId, UUID shooterId, MoveType moveType) {
        return moveRepository.countByMatchIdAndShooterIdAndMoveType(matchId, shooterId, moveType);
    }

    @Override
    public long countUsagesForMoveType(Long matchId, UUID shooterId, MoveType moveType) {
        return moveRepository.countByMatchIdAndShooterIdAndMoveTypeAndIsMoveOriginTrue(matchId, shooterId, moveType);
    }
}
