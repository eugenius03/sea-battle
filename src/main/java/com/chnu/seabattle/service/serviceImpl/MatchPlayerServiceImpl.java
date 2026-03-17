package com.chnu.seabattle.service.serviceImpl;

import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.exception.GameRuleViolationException;
import com.chnu.seabattle.repository.MatchPlayerRepository;
import com.chnu.seabattle.service.AbstractBaseService;
import com.chnu.seabattle.service.MatchPlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchPlayerServiceImpl extends AbstractBaseService<MatchPlayer, UUID> implements MatchPlayerService {

    private final MatchPlayerRepository matchPlayerRepository;

    @Override
    protected MatchPlayerRepository getRepository() {
        return matchPlayerRepository;
    }

    @Override
    public boolean areAllPlayersReady(Long matchId) {
        return matchPlayerRepository.areAllPlayersReady(matchId);
    }

    @Override
    protected void beforeCreate(MatchPlayer matchPlayer) {
        if (matchPlayerRepository.existsByMatchIdAndUserId(matchPlayer.getMatch().getId(), matchPlayer.getUserId())) {
            throw new GameRuleViolationException("Player is already in the match");
        }
    }
}
