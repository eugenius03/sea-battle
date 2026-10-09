package com.chnu.seabattle.dto.user;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        boolean emailVerified,
        Instant memberSince,
        boolean isGuest,
        PlayerCombatStats stats
) {
}
