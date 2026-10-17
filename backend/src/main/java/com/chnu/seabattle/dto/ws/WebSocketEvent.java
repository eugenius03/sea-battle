package com.chnu.seabattle.dto.ws;

public sealed interface WebSocketEvent permits PresenceMessage, MoveMessage,
        MatchmakingStatusResponse, MatchStatusMessage, RematchMessage {
    String destination();
}
