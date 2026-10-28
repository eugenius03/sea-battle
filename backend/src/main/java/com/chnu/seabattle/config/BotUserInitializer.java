package com.chnu.seabattle.config;

import com.chnu.seabattle.constants.BotConstants;
import com.chnu.seabattle.entity.User;
import com.chnu.seabattle.enums.UserType;
import com.chnu.seabattle.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BotUserInitializer {

    private final UserService userService;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBotUser() {
        User bot = userService.findByUsername(BotConstants.BOT_USERNAME).orElse(null);
        if (bot == null) {
            try {
                userService.create(User.builder()
                        .username(BotConstants.BOT_USERNAME)
                        .userType(UserType.BOT)
                        .build());
                log.info("Created bot user {}", BotConstants.BOT_USERNAME);
                return;
            } catch (DataIntegrityViolationException e) {
                bot = userService.findByUsername(BotConstants.BOT_USERNAME).orElseThrow(() -> e);
            }
        }
        if (bot.getUserType() != UserType.BOT) {
            throw new IllegalStateException(
                    "Username " + BotConstants.BOT_USERNAME + " is taken by a non-bot account; rename it before starting the application");
        }
    }
}
