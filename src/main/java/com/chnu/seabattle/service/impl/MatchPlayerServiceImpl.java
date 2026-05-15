package com.chnu.seabattle.service.impl;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.exception.GameRuleViolationException;
import com.chnu.seabattle.repository.MatchPlayerRepository;
import com.chnu.seabattle.service.AbstractBaseService;
import com.chnu.seabattle.service.MatchPlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
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
    public Optional<MatchPlayer> findByMatchInviteTokenAndUserId(String inviteToken, UUID userId) {
        return matchPlayerRepository.findByMatchInviteTokenAndUserId(inviteToken, userId);
    }

    @Override
    public Optional<MatchPlayer> findByMatchInviteTokenAndId(String inviteToken, UUID matchPlayerId) {
        return matchPlayerRepository.findByMatchInviteTokenAndId(inviteToken, matchPlayerId);
    }

    @Override
    public boolean existsByMatchInviteTokenAndId(String inviteToken, UUID matchPlayerId) {
        return matchPlayerRepository.existsByMatchInviteTokenAndId(inviteToken, matchPlayerId);
    }

    @Override
    protected void beforeCreate(MatchPlayer matchPlayer) {
        if (matchPlayerRepository.existsByMatchIdAndUserId(matchPlayer.getMatch().getId(), matchPlayer.getUserId())) {
            throw new GameRuleViolationException(ErrorConstants.PLAYER_ALREADY_IN_MATCH);
        }
    }
}
