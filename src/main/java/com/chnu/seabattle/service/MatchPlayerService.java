package com.chnu.seabattle.service;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.exception.GameRuleViolationException;
import com.chnu.seabattle.repository.MatchPlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchPlayerService extends AbstractBaseService<MatchPlayer, UUID> {

    private final MatchPlayerRepository matchPlayerRepository;

    @Override
    protected MatchPlayerRepository getRepository() {
        return matchPlayerRepository;
    }

    public Optional<MatchPlayer> findByMatchInviteTokenAndUserId(String inviteToken, UUID userId) {
        return matchPlayerRepository.findByMatchInviteTokenAndUserId(inviteToken, userId);
    }

    public Optional<MatchPlayer> findByMatchInviteTokenAndId(String inviteToken, UUID matchPlayerId) {
        return matchPlayerRepository.findByMatchInviteTokenAndId(inviteToken, matchPlayerId);
    }

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
