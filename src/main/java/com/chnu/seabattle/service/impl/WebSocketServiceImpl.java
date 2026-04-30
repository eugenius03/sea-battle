package com.chnu.seabattle.service.impl;

import com.chnu.seabattle.dto.GameInfoResponse;
import com.chnu.seabattle.dto.MatchStatusMessage;
import com.chnu.seabattle.dto.MoveMessage;
import com.chnu.seabattle.dto.PresenceMessage;
import com.chnu.seabattle.dto.PresenseEventType;
import com.chnu.seabattle.dto.move.MoveResponse;
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
    public void sendOpponentMoveMessage(Long matchId, UUID recipientMatchPlayerId,
                                        List<MoveResponse> moveResponses, boolean isItMyTurn
    ) {
        MoveMessage payload = new MoveMessage(
                matchId,
                recipientMatchPlayerId,
                moveResponses,
                Instant.now(),
                isItMyTurn
        );

        messagingTemplate.convertAndSendToUser(
                recipientMatchPlayerId.toString(),
                privateQueue(matchId),
                payload
        );

    }

    @Override
    public void sendReconnectData(
            Long matchId,
            UUID recipientMatchPlayerId,
            GameInfoResponse gameInfoResponse
    ) {
        messagingTemplate.convertAndSendToUser(
                recipientMatchPlayerId.toString(),
                privateQueue(matchId),
                gameInfoResponse
        );
    }
}
