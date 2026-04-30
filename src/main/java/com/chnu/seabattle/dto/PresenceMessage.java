package com.chnu.seabattle.dto;

import com.chnu.seabattle.dto.move.MoveResponse;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PresenceMessage {
    Long matchId;
    UUID matchPlayerId;
    PresenseEventType presenseEventType;
    Instant at;
    List<MoveResponse> moveResponses;
}
