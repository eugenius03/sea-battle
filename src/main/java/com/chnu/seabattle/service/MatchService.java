package com.chnu.seabattle.service;

import com.chnu.seabattle.dto.match.MatchResponse;
import com.chnu.seabattle.entity.Match;

import java.util.Optional;
import java.util.UUID;

public interface MatchService extends BaseService<Match, Long> {

    Match createMatch(UUID userId);

    Match createMatch(UUID player1Id, UUID player2Id);

    Optional<Match> findByInviteTokenForGame(String inviteToken);

    Match joinMatch(UUID userId, String inviteToken);

    Match getByInviteToken(String inviteToken);

    Optional<MatchResponse> processRematch(String inviteToken, UUID userId);
}
