package com.chnu.seabattle.dto.user;

import com.chnu.seabattle.enums.UserType;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        boolean emailVerified,
        Instant memberSince,
        UserType userType,
        PlayerCombatStats stats
) {
}
