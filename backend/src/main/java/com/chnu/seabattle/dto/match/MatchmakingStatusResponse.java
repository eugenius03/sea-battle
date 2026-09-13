package com.chnu.seabattle.dto.match;

public record MatchmakingStatusResponse(
        Long queuePosition, String inviteToken
) {
}
