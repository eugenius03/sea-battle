package com.chnu.seabattle.dto.ws;

import com.chnu.seabattle.enums.EventTopic;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;

import java.util.UUID;

@Builder
public record PresenceMessage(
        @JsonIgnore
        String inviteToken,
        UUID matchPlayerId,
        PresenceEventType presenceEventType
) implements WebSocketEvent {

    @Override
    public String destination() {
        return EventTopic.PRESENCE.forId(inviteToken);
    }
}
