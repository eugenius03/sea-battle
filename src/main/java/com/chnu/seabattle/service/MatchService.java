package com.chnu.seabattle.service;

import com.chnu.seabattle.entity.Match;

import java.util.UUID;

public interface MatchService {

//    UUID generateGuestId();

    Match createMatch(UUID playerId);

    Match joinMatch(UUID playerId, String inviteToken);

    Match getMatchById(Long matchId);

    Match getMatchByInviteToken(String inviteToken);
}
