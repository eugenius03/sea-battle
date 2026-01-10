package com.chnu.seabattle.dto;

import com.chnu.seabattle.entity.MatchStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class MatchStatusMessage {
    Long matchId;
    MatchStatus matchStatus;
    Instant at;
    UUID currentTurnPlayerId;
}
