package com.chnu.seabattle.service;

import com.chnu.seabattle.entity.Match;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MatchService extends BaseService<Match, Long> {

    Match createMatch(UUID userId);

    Optional<Match> findByIdForGame(@Param("id") Long id);

    Match joinMatch(UUID userId, String inviteToken);

    Match getMatchByInviteToken(String inviteToken);
}
