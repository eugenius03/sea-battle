package com.chnu.seabattle.config;

import com.chnu.seabattle.constants.BotConstants;
import com.chnu.seabattle.entity.User;
import com.chnu.seabattle.enums.UserType;
import com.chnu.seabattle.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BotUserInitializer Tests")
class BotUserInitializerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private BotUserInitializer initializer;

    private User user(UserType type) {
        return User.builder().username(BotConstants.BOT_USERNAME).userType(type).build();
    }

    @Test
    @DisplayName("Creates the bot with no password when it is missing")
    void createsBotWhenMissing() {
        when(userService.findByUsername(BotConstants.BOT_USERNAME)).thenReturn(Optional.empty());

        initializer.ensureBotUser();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).create(captor.capture());
        assertEquals(BotConstants.BOT_USERNAME, captor.getValue().getUsername());
        assertEquals(UserType.BOT, captor.getValue().getUserType());
        assertNull(captor.getValue().getPasswordHash());
    }

    @Test
    @DisplayName("Does nothing when the bot already exists")
    void doesNothingWhenBotExists() {
        when(userService.findByUsername(BotConstants.BOT_USERNAME)).thenReturn(Optional.of(user(UserType.BOT)));

        initializer.ensureBotUser();

        verify(userService, never()).create(any());
    }

    @Test
    @DisplayName("Fails startup when a non-bot account holds the bot name")
    void failsWhenNameTakenByNonBot() {
        when(userService.findByUsername(BotConstants.BOT_USERNAME)).thenReturn(Optional.of(user(UserType.REGISTERED)));

        assertThrows(IllegalStateException.class, () -> initializer.ensureBotUser());
        verify(userService, never()).create(any());
    }

    @Test
    @DisplayName("Accepts the bot created by another instance after a unique-name race")
    void acceptsBotCreatedByRacingInstance() {
        when(userService.findByUsername(BotConstants.BOT_USERNAME))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(user(UserType.BOT)));
        when(userService.create(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        initializer.ensureBotUser();
    }

    @Test
    @DisplayName("Fails after a unique-name race if the winner is not a bot")
    void failsAfterRaceWhenWinnerIsNotBot() {
        when(userService.findByUsername(BotConstants.BOT_USERNAME))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(user(UserType.GUEST)));
        when(userService.create(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(IllegalStateException.class, () -> initializer.ensureBotUser());
    }
}
