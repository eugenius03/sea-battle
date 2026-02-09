package com.chnu.seabattle.service;

import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MoveResult;
import com.chnu.seabattle.entity.Orientation;
import com.chnu.seabattle.entity.Ship;
import com.chnu.seabattle.entity.ShipType;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface GameService {

    Ship placeShip(
            Long matchId,
            UUID playerId,
            ShipType type,
            int startX,
            int startY,
            Orientation orientation
    );

    Ship moveShip(Long matchId, UUID playerId, Long shipId, int x, int y, Orientation orientation);

    Match markReady(Long matchId, UUID playerId);

    MoveResult fire(
            Long matchId,
            UUID shooterId,
            int x,
            int y
    );

    void handleDisconnect(Long matchId, UUID playerId);

    void handleReconnect(Long matchId, UUID playerId);

    UUID getOpponentPlayerId(Match match, UUID playerId);

}
