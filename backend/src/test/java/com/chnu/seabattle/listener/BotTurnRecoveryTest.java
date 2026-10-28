package com.chnu.seabattle.listener;

import com.chnu.seabattle.constants.BotConstants;
import com.chnu.seabattle.dto.match.BotTurnEvent;
import com.chnu.seabattle.entity.User;
import com.chnu.seabattle.enums.UserType;
import com.chnu.seabattle.service.MatchService;
import com.chnu.seabattle.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("BotTurnRecovery Tests")
class BotTurnRecoveryTest {

    private final UserService userService = mock(UserService.class);
    private final MatchService matchService = mock(MatchService.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final BotTurnRecovery recovery = new BotTurnRecovery(userService, matchService, publisher);

    @Test
    @DisplayName("Publishes a bot turn for every match where the bot is on turn")
    void resumesEachMatch() {
        User bot = User.builder().username(BotConstants.BOT_USERNAME).userType(UserType.BOT).build();
        bot.setId(UUID.randomUUID());
        when(userService.findByUsername(BotConstants.BOT_USERNAME)).thenReturn(Optional.of(bot));
        when(matchService.findInProgressInviteTokensOnTurnOf(bot.getId())).thenReturn(List.of("a", "b"));

        recovery.resumeInterruptedTurns();

        verify(publisher).publishEvent(new BotTurnEvent("a"));
        verify(publisher).publishEvent(new BotTurnEvent("b"));
    }

    @Test
    @DisplayName("Does nothing when the bot user does not exist yet")
    void noBotNoRecovery() {
        when(userService.findByUsername(BotConstants.BOT_USERNAME)).thenReturn(Optional.empty());

        recovery.resumeInterruptedTurns();

        verifyNoInteractions(matchService, publisher);
    }
}
