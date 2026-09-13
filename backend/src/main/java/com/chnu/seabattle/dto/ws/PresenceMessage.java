package com.chnu.seabattle.dto.ws;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record PresenceMessage(
        UUID matchPlayerId,
        PresenceEventType presenceEventType,
        Instant at,
        String inviteToken
) {
}
