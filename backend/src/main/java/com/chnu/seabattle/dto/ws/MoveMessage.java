package com.chnu.seabattle.dto.ws;

import com.chnu.seabattle.dto.move.MoveResponse;
import com.chnu.seabattle.enums.EventTopic;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.UUID;

public record MoveMessage(
        @JsonIgnore
        UUID recipientMatchPlayerId,
        @JsonIgnore
        String inviteToken,
        List<MoveResponse> moveResponses,
        UUID currentTurnPlayerId
) implements WebSocketEvent, PrivateWebSocketEvent {

    @Override
    public String destination() {
        return EventTopic.PRIVATE.forId(inviteToken);
    }

    @Override
    public String recipient() {
        return recipientMatchPlayerId.toString();
    }
}