package com.chnu.seabattle.service;

import com.chnu.seabattle.dto.game.GameInfoResponse;
import com.chnu.seabattle.dto.move.MoveRequest;
import com.chnu.seabattle.dto.ship.ShipRequest;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.Move;
import com.chnu.seabattle.entity.Orientation;
import com.chnu.seabattle.entity.Ship;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public interface GameService {

    Ship placeShip(String inviteToken, UUID playerId, ShipRequest shipRequest);

    List<Ship> generateRandomShips(String inviteToken, UUID playerId);

    Ship moveShip(String inviteToken, UUID playerId, Long shipId, int x, int y, Orientation orientation);

    Match markReady(String inviteToken, MatchPlayer player);

    List<Move> executeMove(String inviteToken, UUID shooterId, MoveRequest moveRequest);

    void handleDisconnect(String inviteToken, UUID matchPlayerId);

    void handleReconnect(Match match, MatchPlayer player);
    
    GameInfoResponse getMatchInfo(Match match, MatchPlayer matchPlayer);

}
