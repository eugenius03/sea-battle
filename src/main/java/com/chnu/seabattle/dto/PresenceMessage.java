package com.chnu.seabattle.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PresenceMessage {
    Long matchId;
    UUID matchPlayerId;
    PresenseEventType presenseEventType;
    Instant at;
}
