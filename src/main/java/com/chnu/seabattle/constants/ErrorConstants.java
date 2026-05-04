package com.chnu.seabattle.constants;

public final class ErrorConstants {

    private ErrorConstants() {
    }

    // --- Resource not found ---
    public static final String MATCH_NOT_FOUND = "Match not found";
    public static final String PLAYER_NOT_FOUND = "Player not found";
    public static final String SHIP_NOT_FOUND = "Ship not found";
    public static final String USER_NOT_FOUND = "User not found";
    public static final String AUTHENTICATED_USER_NOT_FOUND = "Authenticated user not found in database";
    public static final String ENTITY_NOT_FOUND = "Entity not found";

    // --- Authentication / Authorization ---
    public static final String USERNAME_ALREADY_EXISTS = "Username already exists";
    public static final String REFRESH_TOKEN_EXPIRED = "Refresh token expired";
    public static final String INVALID_TOKEN = "Invalid token";
    public static final String USER_NOT_AUTHENTICATED = "User is not authenticated";
    public static final String SHORT_PASSWORD = "Your password is too short";

    // --- Match rules ---
    public static final String ONLY_TWO_PLAYERS = "Match must have exactly 2 players";
    public static final String MATCH_NOT_JOINABLE = "Match is not joinable";
    public static final String PLAYER_ALREADY_IN_MATCH = "Player is already in the match";
    public static final String NOT_A_PLAYER_IN_MATCH = "You are not a player in this match";
    public static final String INVALID_MATCH_STATE = "Action not allowed in match state: %s";

    // --- Game rules ---
    public static final String NOT_PLAYERS_TURN = "It's not the player's turn";
    public static final String PLAYER_ALREADY_READY = "Action unsupported: player is already ready";
    public static final String DUPLICATE_FIRE = "Cannot fire at the same coordinates twice";
    public static final String COORDINATES_OUT_OF_BOUNDS = "Coordinates out of bounds";
    public static final String DRONE_LIMIT_EXCEEDED = "You've hit your limit of attacking with this drone";

    // --- Ship placement ---
    public static final String INVALID_SHIP_PLACEMENT = "Invalid ship placement: out of bounds or overlapping";
    public static final String SHIP_TYPE_LIMIT = "Ship limit reached for type: %s";

    // --- Move strategy ---
    public static final String UNKNOWN_MOVE_TYPE = "Unknown or unimplemented move type: %s";

    // --- WebSocket / STOMP ---
    public static final String MISSING_MATCH_HEADERS = "Missing X-Invite-Token / X-Match-Player-Id";
    public static final String INVALID_MATCH_HEADERS = "Invalid X-Invite-Token / X-Match-Player-Id";

    public static final String SHIP_GENERATION_FAILED = "Failed to generate a valid board layout after maximum attempts";
}