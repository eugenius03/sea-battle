package com.chnu.seabattle.service;

import com.chnu.seabattle.dto.FireResult;
import com.chnu.seabattle.dto.GameInfoResponse;
import com.chnu.seabattle.dto.ship.ShipResponse;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.Orientation;
import com.chnu.seabattle.entity.Ship;
import com.chnu.seabattle.entity.ShipType;
import org.springframework.stereotype.Service;

import java.util.List;
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

    List<ShipResponse> generateRandomShips(Long matchId, UUID playerId);

    Ship moveShip(Long matchId, UUID playerId, Long shipId, int x, int y, Orientation orientation);

    Match markReady(Long matchId, UUID playerId);

    FireResult fire(
            Long matchId,
            UUID shooterId,
            int x,
            int y
    );

    void handleDisconnect(Match match, MatchPlayer player);

    void handleReconnect(Match match, MatchPlayer player);

    UUID getOpponentPlayerId(Match match, UUID playerId);

    GameInfoResponse getMatchInfo(Long matchId, MatchPlayer matchPlayer);

}
