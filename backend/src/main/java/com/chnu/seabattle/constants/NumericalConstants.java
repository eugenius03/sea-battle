package com.chnu.seabattle.constants;

public final class NumericalConstants {

    private NumericalConstants() {
    }

    // Board & Game Grid
    public static final int GRID_SIZE = 10;
    public static final int MAX_SHIP_PLACEMENT_ATTEMPTS = 100;
    public static final int MAX_BOARD_GENERATION_RETRIES = 50;

    // Guest Auth
    public static final int MAX_GUEST_USERNAME_RETRIES = 5;
    public static final int GUEST_SUFFIX_MIN = 1000;
    public static final int GUEST_SUFFIX_MAX = 10000;
    public static final int GUEST_FALLBACK_SUFFIX_MIN = 100000;
    public static final int GUEST_FALLBACK_SUFFIX_MAX = 1000000;

    // Drone Attacks
    public static final int ATTACK_DRONE_BASE_SHOTS = 3;
    public static final int ATTACK_DRONE_RANDOM_SHOTS_BOUND = 4;
}
