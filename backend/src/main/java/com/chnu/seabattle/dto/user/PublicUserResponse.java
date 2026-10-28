package com.chnu.seabattle.dto.user;

import com.chnu.seabattle.enums.UserType;

import java.time.Instant;

public record PublicUserResponse(
        String username,
        Instant memberSince,
        UserType userType,
        PlayerCombatStats stats
) {
}
