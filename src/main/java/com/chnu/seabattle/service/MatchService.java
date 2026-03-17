package com.chnu.seabattle.service;

import com.chnu.seabattle.entity.Match;

import java.util.UUID;

public interface MatchService extends BaseService<Match, Long> {

//    UUID generateGuestId();

    Match createMatch(UUID playerId);

    Match joinMatch(UUID playerId, String inviteToken);
    
    Match getMatchByInviteToken(String inviteToken);
}
