package com.chnu.seabattle.dto;

import com.chnu.seabattle.dto.move.MoveResponse;
import com.chnu.seabattle.dto.ship.ShipResponse;
import com.chnu.seabattle.entity.MatchStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Builder
@Getter
@Setter
public class GameInfoResponse {

    private List<ShipResponse> playerShips;
    private List<MoveResponse> playerMoves;
    private List<MoveResponse> opponentMoves;
    private MatchStatus matchStatus;
    private UUID matchPlayerId;
    private Long matchId;

    @JsonProperty("isItMyTurn")
    private boolean isItMyTurn;

}



