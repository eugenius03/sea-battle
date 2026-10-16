package com.chnu.seabattle.dto.ws;

import com.chnu.seabattle.dto.move.MoveResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MoveMessage(
        UUID recipientMatchPlayerId,
        List<MoveResponse> moveResponses,
        Instant at,
        UUID currentTurnPlayerId
) {
}