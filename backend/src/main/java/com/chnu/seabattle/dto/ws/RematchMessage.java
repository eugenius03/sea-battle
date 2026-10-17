package com.chnu.seabattle.dto.ws;

import com.chnu.seabattle.enums.EventTopic;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record RematchMessage(
        @JsonIgnore
        String inviteToken,
        UUID matchPlayerId,
        PresenceEventType presenceEventType,
        @JsonProperty("inviteToken")
        String newInviteToken

) implements WebSocketEvent {

    @Override
    public String destination() {
        return EventTopic.PRESENCE.forId(inviteToken);
    }
}
