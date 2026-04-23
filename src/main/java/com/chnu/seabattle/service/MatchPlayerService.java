package com.chnu.seabattle.service;

import com.chnu.seabattle.entity.MatchPlayer;

import java.util.Optional;
import java.util.UUID;

public interface MatchPlayerService extends BaseService<MatchPlayer, UUID> {

    Optional<MatchPlayer> findByMatchIdAndUserId(
            Long matchId, UUID userId
    );
}
