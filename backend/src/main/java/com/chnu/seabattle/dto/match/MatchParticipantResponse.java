package com.chnu.seabattle.dto.match;

import com.chnu.seabattle.dto.ship.ShipResponse;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record MatchParticipantResponse(
        UUID playerId,
        UUID userId,
        String username,
        boolean isWinner,
        List<ShipResponse> ships
) {
}
