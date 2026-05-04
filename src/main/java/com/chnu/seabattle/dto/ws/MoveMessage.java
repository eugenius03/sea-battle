package com.chnu.seabattle.dto.ws;

import com.chnu.seabattle.dto.move.MoveResponse;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MoveMessage(
        UUID recipientMatchPlayerId,
        List<MoveResponse> moveResponses,
        Instant at,

        @JsonProperty("isItMyTurn")
        boolean isItMyTurn
) {
}