package com.chnu.seabattle.listener;

import com.chnu.seabattle.dto.move.MoveResponse;
import com.chnu.seabattle.dto.ws.MatchStatusMessage;
import com.chnu.seabattle.dto.ws.MoveMessage;
import com.chnu.seabattle.dto.ws.PresenceEventType;
import com.chnu.seabattle.dto.ws.PresenceMessage;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.entity.MoveResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketEventListener")
class WebSocketEventListenerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WebSocketEventListener listener;

    @Test
    @DisplayName("Topic events are broadcast to their destination")
    void topicEventIsBroadcast() {
        PresenceMessage event = new PresenceMessage("t1", UUID.randomUUID(), PresenceEventType.DISCONNECTED);

        listener.on(event);

        verify(messagingTemplate).convertAndSend("/topic/match/t1/presence", event);
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Status events go to the status topic")
    void statusEventGoesToStatusTopic() {
        MatchStatusMessage event = new MatchStatusMessage("t1", MatchStatus.PLANNING, null, null);

        listener.on(event);

        verify(messagingTemplate).convertAndSend("/topic/match/t1/status", event);
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Private events are sent to the recipient's user queue only")
    void privateEventGoesToUserQueue() {
        UUID recipient = UUID.randomUUID();
        MoveMessage event = new MoveMessage(recipient, "t1",
                List.of(new MoveResponse(0, 0, MoveResult.MISS)), UUID.randomUUID());

        listener.on(event);

        verify(messagingTemplate).convertAndSendToUser(recipient.toString(), "/queue/match/t1", event);
        verifyNoMoreInteractions(messagingTemplate);
    }
}
