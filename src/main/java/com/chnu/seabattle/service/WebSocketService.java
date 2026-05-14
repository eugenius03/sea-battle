package com.chnu.seabattle.service;

import com.chnu.seabattle.dto.move.MoveResponse;
import com.chnu.seabattle.dto.ship.ShipResponse;
import com.chnu.seabattle.entity.MatchStatus;

import java.util.List;
import java.util.UUID;

public interface WebSocketService {

    void handleOpponentConnected(String inviteToken, UUID matchPlayerId);

    void handleDisconnect(String inviteToken, UUID matchPlayerId);

    void sendPlayerReadyMessage(String inviteToken, UUID matchPlayerId);

    void handleReconnect(String inviteToken, UUID matchPlayerId);

    void updateMatchStatus(String inviteToken, MatchStatus matchStatus, UUID currentTurnPlayerId, List<ShipResponse> winnerShips);

    void sendOpponentMoveMessage(String inviteToken, UUID recipientMatchPlayerId,
                                 List<MoveResponse> moveResponses, boolean isItMyTurn
    );

    void sendRematchRequested(String inviteToken, UUID matchPlayerId);

    void sendRematchAgreed(String inviteToken, String newInviteToken);

    void sendQueuePosition(String matchmakingId, Long position);

    void sendMatchFound(String matchmakingId, String inviteToken);
}