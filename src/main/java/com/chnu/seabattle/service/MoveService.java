package com.chnu.seabattle.service;

import com.chnu.seabattle.entity.Move;
import com.chnu.seabattle.entity.MoveType;
import com.chnu.seabattle.repository.MoveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MoveService extends AbstractBaseService<Move, Long> {

    private final MoveRepository moveRepository;

    @Override
    protected MoveRepository getRepository() {
        return moveRepository;
    }

    public boolean existsByMatchIdAndShooterIdAndTargetXAndTargetYAndMoveType(Long matchId, UUID shooterId, Integer x, Integer y, MoveType moveType) {
        return moveRepository.existsByMatchIdAndShooterIdAndTargetXAndTargetYAndMoveType(matchId, shooterId, x, y, moveType);
    }

    public List<Move> findByMatchIdAndShooterId(Long matchId, UUID shooterId) {
        return moveRepository.findByMatchIdAndShooterId(matchId, shooterId);
    }

    public long countByMatchIdAndShooterIdAndMoveType(Long matchId, UUID shooterId, MoveType moveType) {
        return moveRepository.countByMatchIdAndShooterIdAndMoveType(matchId, shooterId, moveType);
    }

    public long countUsagesForMoveType(Long matchId, UUID shooterId, MoveType moveType) {
        return moveRepository.countByMatchIdAndShooterIdAndMoveTypeAndIsMoveOriginTrue(matchId, shooterId, moveType);
    }
}
