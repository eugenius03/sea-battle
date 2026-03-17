package com.chnu.seabattle.service;

import com.chnu.seabattle.entity.MatchPlayer;

import java.util.UUID;

public interface MatchPlayerService extends BaseService<MatchPlayer, UUID> {

    boolean areAllPlayersReady(Long matchId);
}
