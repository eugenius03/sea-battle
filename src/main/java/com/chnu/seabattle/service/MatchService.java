package com.chnu.seabattle.service;

import com.chnu.seabattle.entity.Match;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public interface MatchService {

//    UUID generateGuestId();

    Match createMatch(UUID playerId);

    Match joinMatch(UUID playerId, String inviteToken);

    Match getMatchById(Long matchId);

    Match getMatchByInviteToken(String inviteToken);
}
