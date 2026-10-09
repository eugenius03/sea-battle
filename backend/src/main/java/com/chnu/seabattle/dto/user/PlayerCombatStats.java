package com.chnu.seabattle.dto.user;

import lombok.Builder;

@Builder
public record PlayerCombatStats(
        long matchesPlayed,
        long wins,
        long losses,
        double winRate,
        long shotsFired,
        long hits,
        long misses,
        double accuracy,
        int currentWinStreak
) {
}