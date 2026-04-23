package com.chnu.seabattle.service;

import com.chnu.seabattle.dto.GameInfoResponse;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.entity.MoveResult;

import java.util.UUID;

public interface WebSocketService {

    void handleOpponentConnected(Long matchId, UUID matchPlayerId);

    void handleDisconnect(Long matchId, UUID matchPlayerId);

    void sendPlayerReadyMessage(Long matchId, UUID matchPlayerId);

    void handleReconnect(Long matchId, UUID matchPlayerId);

    void updateMatchStatus(Long matchId, MatchStatus matchStatus, UUID currentTurnPlayerId);

    void sendOpponentMoveMessage(
            Long matchId,
            UUID recipientMatchPlayerId,
            int x,
            int y,
            MoveResult moveResult,
            boolean isItMyTurn
    );

    void sendReconnectData(
            Long matchId,
            UUID recipientMatchPlayerId,
            GameInfoResponse gameInfoResponse
    );
}
