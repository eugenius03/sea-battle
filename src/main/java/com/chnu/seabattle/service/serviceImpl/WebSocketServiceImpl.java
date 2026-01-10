package com.chnu.seabattle.service.serviceImpl;

import com.chnu.seabattle.dto.MatchStatusMessage;
import com.chnu.seabattle.dto.MoveMessage;
import com.chnu.seabattle.dto.PresenceMessage;
import com.chnu.seabattle.dto.PresenseEventType;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.entity.MoveResult;
import com.chnu.seabattle.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WebSocketServiceImpl implements WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    private String presenceTopic(Long matchId) {
        return "/topic/match/" + matchId + "/presence";
    }

    private String statusTopic(Long matchId) {
        return "/topic/match/" + matchId + "/status";
    }

    private String privateQueue(Long matchId) {
        return "/queue/match/" + matchId;
    }

    @Override
    public void handleOpponentConnected(Long matchId, UUID matchPlayerId) {
        messagingTemplate.convertAndSend(
                presenceTopic(matchId),
                PresenceMessage.builder()
                        .matchId(matchId)
                        .matchPlayerId(matchPlayerId)
                        .presenseEventType(PresenseEventType.OPPONENT_CONNECTED)
                        .at(Instant.now())
                        .build()
        );
    }

    @Override
    public void handleDisconnect(Long matchId, UUID matchPlayerId) {
        messagingTemplate.convertAndSend(
                presenceTopic(matchId),
                PresenceMessage.builder()
                        .matchId(matchId)
                        .matchPlayerId(matchPlayerId)
                        .presenseEventType(PresenseEventType.DISCONNECTED)
                        .at(Instant.now())
                        .build()
        );
    }

    @Override
    public void sendPlayerReadyMessage(Long matchId, UUID matchPlayerId) {
        messagingTemplate.convertAndSend(
                presenceTopic(matchId),
                PresenceMessage.builder()
                        .matchId(matchId)
                        .matchPlayerId(matchPlayerId)
                        .presenseEventType(PresenseEventType.OPPONENT_READY)
                        .at(Instant.now())
                        .build()
        );
    }

    @Override
    public void handleReconnect(Long matchId, UUID matchPlayerId) {
        messagingTemplate.convertAndSend(
                presenceTopic(matchId),
                PresenceMessage.builder()
                        .matchId(matchId)
                        .matchPlayerId(matchPlayerId)
                        .presenseEventType(PresenseEventType.RECONNECTED)
                        .at(Instant.now())
                        .build()
        );
    }

    @Override
    public void updateMatchStatus(Long matchId, MatchStatus matchStatus, UUID currentTurnPlayerId) {
        messagingTemplate.convertAndSend(
                statusTopic(matchId),
                MatchStatusMessage.builder()
                        .matchId(matchId)
                        .matchStatus(matchStatus)
                        .at(Instant.now())
                        .currentTurnPlayerId(currentTurnPlayerId)
                        .build()
        );
    }

    @Override
    public void sendOpponentMoveMessage(
            Long matchId,
            UUID recipientMatchPlayerId,
            int x,
            int y,
            MoveResult moveResult,
            UUID nextTurnPlayerId
    ) {
        MoveMessage payload = MoveMessage.builder()
                .matchId(matchId)
                .recipientMatchPlayerId(recipientMatchPlayerId)
                .x(x)
                .y(y)
                .result(moveResult)
                .at(Instant.now())
                .nextTurnPlayerId(nextTurnPlayerId)
                .build();

        messagingTemplate.convertAndSendToUser(
                recipientMatchPlayerId.toString(),
                privateQueue(matchId),
                payload
        );

    }
}
