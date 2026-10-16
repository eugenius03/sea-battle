package com.chnu.seabattle.dto.game;

import com.chnu.seabattle.dto.move.MoveResponse;

import java.util.List;
import java.util.UUID;

public record FireResponse(List<MoveResponse> moveResponses, UUID currentTurnPlayerId) {
}
