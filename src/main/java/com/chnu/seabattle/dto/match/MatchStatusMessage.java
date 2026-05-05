package com.chnu.seabattle.dto.match;

import com.chnu.seabattle.dto.ship.ShipResponse;
import com.chnu.seabattle.entity.MatchStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record MatchStatusMessage(
        String inviteToken,
        MatchStatus matchStatus,
        Instant at,
        UUID currentTurnPlayerId,
        List<ShipResponse> winnerShips
) {
}