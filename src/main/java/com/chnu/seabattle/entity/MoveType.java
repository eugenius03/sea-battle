package com.chnu.seabattle.entity;

public enum MoveType {
    STANDARD(Integer.MAX_VALUE),
    SURVEILLANCE_DRONE(1),
    ATTACK_DRONE(2);

    private final int maxUsesPerMatch;

    MoveType(int maxUsesPerMatch) {
        this.maxUsesPerMatch = maxUsesPerMatch;
    }

    public int getMaxUsesPerMatch() {
        return maxUsesPerMatch;
    }
}
