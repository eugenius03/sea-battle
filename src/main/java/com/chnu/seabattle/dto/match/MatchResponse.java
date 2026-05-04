package com.chnu.seabattle.dto.match;

import java.util.UUID;

public record MatchResponse(
        String inviteToken, UUID playerId
) {

}
