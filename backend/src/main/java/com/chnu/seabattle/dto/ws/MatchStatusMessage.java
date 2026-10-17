package com.chnu.seabattle.dto.ws;

import com.chnu.seabattle.dto.ship.ShipResponse;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.enums.EventTopic;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record MatchStatusMessage(
        @JsonIgnore
        String inviteToken,
        MatchStatus matchStatus,
        UUID currentTurnPlayerId,
        List<ShipResponse> winnerShips
) implements WebSocketEvent {

    @Override
    public String destination() {
        return EventTopic.STATUS.forId(inviteToken);
    }
}