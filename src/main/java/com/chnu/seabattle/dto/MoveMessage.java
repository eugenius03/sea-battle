package com.chnu.seabattle.dto;

import com.chnu.seabattle.entity.MoveResult;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class MoveMessage {

    Long matchId;
    UUID recipientMatchPlayerId;
    int x;
    int y;
    MoveResult result;
    Instant at;
    UUID nextTurnPlayerId;
}
