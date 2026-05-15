package com.chnu.seabattle.service.impl;

import com.chnu.seabattle.dto.match.MatchStatusMessage;
import com.chnu.seabattle.dto.match.MatchmakingStatusResponse;
import com.chnu.seabattle.dto.move.MoveResponse;
import com.chnu.seabattle.dto.ship.ShipResponse;
import com.chnu.seabattle.dto.ws.MoveMessage;
import com.chnu.seabattle.dto.ws.PresenceEventType;
import com.chnu.seabattle.dto.ws.PresenceMessage;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketServiceImpl implements WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    private String presenceTopic(String inviteToken) {
        return "/topic/match/" + inviteToken + "/presence";
    }

    private String statusTopic(String inviteToken) {
        return "/topic/match/" + inviteToken + "/status";
    }

    private String privateQueue(String inviteToken) {
        return "/queue/match/" + inviteToken;
    }

    private String matchmakingTopic(String matchmakingId) {
        return "/topic/matchmaking/" + matchmakingId;
    }

    @Override
    public void handleOpponentConnected(String inviteToken, UUID matchPlayerId) {
        messagingTemplate.convertAndSend(
                presenceTopic(inviteToken),
                PresenceMessage.builder()
                        .matchPlayerId(matchPlayerId)
                        .presenceEventType(PresenceEventType.OPPONENT_CONNECTED)
                        .at(Instant.now())
                        .build()
        );
    }

    @Override
    public void handleDisconnect(String inviteToken, UUID matchPlayerId) {
        messagingTemplate.convertAndSend(
                presenceTopic(inviteToken),
                PresenceMessage.builder()
                        .matchPlayerId(matchPlayerId)
                        .presenceEventType(PresenceEventType.DISCONNECTED)
                        .at(Instant.now())
                        .build()
        );
    }

    @Override
    public void sendPlayerReadyMessage(String inviteToken, UUID matchPlayerId) {
        messagingTemplate.convertAndSend(
                presenceTopic(inviteToken),
                PresenceMessage.builder()
                        .matchPlayerId(matchPlayerId)
                        .presenceEventType(PresenceEventType.OPPONENT_READY)
                        .at(Instant.now())
                        .build()
        );
    }

    @Override
    public void handleReconnect(String inviteToken, UUID matchPlayerId) {
        messagingTemplate.convertAndSend(
                presenceTopic(inviteToken),
                PresenceMessage.builder()
                        .matchPlayerId(matchPlayerId)
                        .presenceEventType(PresenceEventType.RECONNECTED)
                        .at(Instant.now())
                        .build()
        );
    }

    @Override
    public void updateMatchStatus(String inviteToken, MatchStatus matchStatus, UUID currentTurnPlayerId, List<ShipResponse> winnerShips) {
        messagingTemplate.convertAndSend(
                statusTopic(inviteToken),
                MatchStatusMessage.builder()
                        .inviteToken(inviteToken)
                        .matchStatus(matchStatus)
                        .at(Instant.now())
                        .currentTurnPlayerId(currentTurnPlayerId)
                        .winnerShips(winnerShips)
                        .build()
        );
    }

    @Override
    public void sendOpponentMoveMessage(String inviteToken, UUID recipientMatchPlayerId,
                                        List<MoveResponse> moveResponses, boolean isItMyTurn
    ) {
        MoveMessage payload = new MoveMessage(
                recipientMatchPlayerId,
                moveResponses,
                Instant.now(),
                isItMyTurn
        );

        messagingTemplate.convertAndSendToUser(
                recipientMatchPlayerId.toString(),
                privateQueue(inviteToken),
                payload
        );
    }

    @Override
    public void sendRematchRequested(String inviteToken, UUID matchPlayerId) {
        messagingTemplate.convertAndSend(
                presenceTopic(inviteToken),
                PresenceMessage.builder()
                        .matchPlayerId(matchPlayerId)
                        .presenceEventType(PresenceEventType.REMATCH_REQUESTED)
                        .at(Instant.now())
                        .build()
        );
    }

    @Override
    public void sendRematchAgreed(String inviteToken, String newInviteToken) {
        messagingTemplate.convertAndSend(
                presenceTopic(inviteToken),
                PresenceMessage.builder()
                        .presenceEventType(PresenceEventType.REMATCH_AGREED)
                        .inviteToken(newInviteToken)
                        .at(Instant.now())
                        .build()
        );
    }

    @Override
    public void sendQueuePosition(String matchmakingId, Long position) {
        messagingTemplate.convertAndSend(
                matchmakingTopic(matchmakingId),
                new MatchmakingStatusResponse(position, null)
        );
    }

    @Override
    public void sendMatchFound(String matchmakingId, String inviteToken) {
        messagingTemplate.convertAndSend(
                matchmakingTopic(matchmakingId),
                new MatchmakingStatusResponse(null, inviteToken)
        );
    }
}