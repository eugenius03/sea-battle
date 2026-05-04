package com.chnu.seabattle.security;

import com.chnu.seabattle.service.GameService;
import com.chnu.seabattle.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final WebSocketService webSocketService;
    private final GameService gameService;

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        if (headerAccessor.getSessionAttributes() != null) {
            String inviteToken = (String) headerAccessor.getSessionAttributes().get("inviteToken");
            UUID matchPlayerId = (UUID) headerAccessor.getSessionAttributes().get("matchPlayerId");

            if (inviteToken != null && matchPlayerId != null) {
                log.info("User disconnected from match: inviteToken={}, matchPlayerId={}", inviteToken, matchPlayerId);
                webSocketService.handleDisconnect(inviteToken, matchPlayerId);
                gameService.handleDisconnect(inviteToken, matchPlayerId);
            } else {
                log.warn("WebSocket disconnect event received but missing inviteToken or matchPlayerId");
            }
        } else {
            log.warn("WebSocket disconnect event received but session attributes are null");
        }
    }
}
