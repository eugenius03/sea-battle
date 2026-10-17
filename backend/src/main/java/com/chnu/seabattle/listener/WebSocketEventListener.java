package com.chnu.seabattle.listener;

import com.chnu.seabattle.dto.ws.PrivateWebSocketEvent;
import com.chnu.seabattle.dto.ws.WebSocketEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(fallbackExecution = true)
    public void on(WebSocketEvent event) {
        if (event instanceof PrivateWebSocketEvent p) {
            messagingTemplate.convertAndSendToUser(p.recipient(), event.destination(), event);
        } else {
            messagingTemplate.convertAndSend(event.destination(), event);
        }
    }
}
