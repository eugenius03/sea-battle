package com.chnu.seabattle.service.serviceImpl;

import com.chnu.seabattle.entity.Move;
import com.chnu.seabattle.repository.MoveRepository;
import com.chnu.seabattle.service.AbstractBaseService;
import com.chnu.seabattle.service.MoveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    public boolean existsByMatchIdAndShooterIdAndTargetXAndTargetY(Long matchId, UUID shooterId, Integer x, Integer y) {
        return moveRepository.existsByMatchIdAndShooterIdAndTargetXAndTargetY(matchId, shooterId, x, y);
    }
}
