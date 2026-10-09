package com.chnu.seabattle.dto.match;

import com.chnu.seabattle.dto.ship.ShipResponse;
import lombok.Builder;

import java.util.List;

@Builder
public record MatchParticipantResponse(
        String username,
        boolean isWinner,
        List<ShipResponse> ships
) {
}
