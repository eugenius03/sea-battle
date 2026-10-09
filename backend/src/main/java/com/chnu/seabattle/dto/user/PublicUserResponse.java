package com.chnu.seabattle.dto.user;

import java.time.Instant;

public record PublicUserResponse(
        String username,
        Instant memberSince,
        boolean isGuest,
        PlayerCombatStats stats
) {
}
