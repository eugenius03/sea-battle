package com.chnu.seabattle.listener;

import com.chnu.seabattle.service.GameBotService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("BotTurnListener Tests")
class BotTurnListenerTest {

    private final GameBotService gameBotService = mock(GameBotService.class);
    private final BotTurnListener listener = new BotTurnListener(gameBotService);

    @Test
    @DisplayName("A successful turn does not trigger the fallback")
    void noFallbackOnSuccess() {
        listener.play("tok");

        verify(gameBotService).playTurn("tok");
        verify(gameBotService, never()).playFallbackTurn("tok");
    }

    @Test
    @DisplayName("A failed turn is retried once as a fallback turn")
    void fallbackOnFailure() {
        doThrow(new IllegalStateException("boom")).when(gameBotService).playTurn("tok");

        listener.play("tok");

        verify(gameBotService).playFallbackTurn("tok");
    }

    @Test
    @DisplayName("A failing fallback is swallowed and does not propagate")
    void fallbackFailureSwallowed() {
        doThrow(new IllegalStateException("boom")).when(gameBotService).playTurn("tok");
        doThrow(new IllegalStateException("boom again")).when(gameBotService).playFallbackTurn("tok");

        listener.play("tok");

        verify(gameBotService).playFallbackTurn("tok");
    }
}
