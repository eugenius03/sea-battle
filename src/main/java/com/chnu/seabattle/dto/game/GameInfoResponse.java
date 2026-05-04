package com.chnu.seabattle.dto.game;

import com.chnu.seabattle.dto.move.MoveResponse;
import com.chnu.seabattle.dto.ship.ShipResponse;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.entity.MoveType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record GameInfoResponse(
        List<ShipResponse> playerShips,
        List<MoveResponse> playerMoves,
        List<MoveResponse> opponentMoves,
        MatchStatus matchStatus,
        UUID matchPlayerId,
        Map<MoveType, Long> droneUsage,

        @JsonProperty("isItMyTurn")
        boolean isItMyTurn
) {

}



