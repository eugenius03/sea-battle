package com.chnu.seabattle.listener;

import com.chnu.seabattle.constants.BotConstants;
import com.chnu.seabattle.dto.match.BotTurnEvent;
import com.chnu.seabattle.service.GameBotService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

@Slf4j
@Component
public class BotTurnListener {

    private final GameBotService gameBotService;
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

    public BotTurnListener(GameBotService gameBotService) {
        this.gameBotService = gameBotService;
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("bot-turn-");
        scheduler.initialize();
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void on(BotTurnEvent event) {
        scheduler.schedule(() -> play(event.inviteToken()), Instant.now().plus(BotConstants.TURN_DELAY));
    }

    void play(String inviteToken) {
        try {
            gameBotService.playTurn(inviteToken);
        } catch (Exception e) {
            log.warn("Bot turn failed in match {}, retrying with a standard shot", inviteToken, e);
            try {
                gameBotService.playFallbackTurn(inviteToken);
            } catch (Exception fallbackError) {
                log.error("Bot fallback turn failed in match {}", inviteToken, fallbackError);
            }
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdown();
    }
}
