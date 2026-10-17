package com.chnu.seabattle.dto.ws;

import com.chnu.seabattle.enums.EventTopic;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record MatchmakingStatusResponse(
        @JsonIgnore
        String matchmakingId,
        Long queuePosition,
        String inviteToken
) implements WebSocketEvent {

    @Override
    public String destination() {
        return EventTopic.MATCHMAKING.forId(matchmakingId);
    }
}
