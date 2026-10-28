package com.chnu.seabattle.listener;

import com.chnu.seabattle.constants.BotConstants;
import com.chnu.seabattle.dto.match.BotTurnEvent;
import com.chnu.seabattle.service.MatchService;
import com.chnu.seabattle.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotTurnRecovery {

    private final UserService userService;
    private final MatchService matchService;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener(ApplicationReadyEvent.class)
    public void resumeInterruptedTurns() {
        userService.findByUsername(BotConstants.BOT_USERNAME).ifPresent(bot -> {
            for (String inviteToken : matchService.findInProgressInviteTokensOnTurnOf(bot.getId())) {
                log.info("Resuming bot turn in match {}", inviteToken);
                eventPublisher.publishEvent(new BotTurnEvent(inviteToken));
            }
        });
    }
}
