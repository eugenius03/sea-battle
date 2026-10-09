package com.chnu.seabattle.dto.match;

import java.util.UUID;

public record MatchInviteResponse(
        String inviteToken, UUID playerId
) {

}
