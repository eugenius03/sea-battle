package com.chnu.seabattle.service;

import com.chnu.seabattle.converter.UserConverter;
import com.chnu.seabattle.dto.auth.UserRegistrationRequest;
import com.chnu.seabattle.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService - reserved bot username")
class AuthServiceReservedNameTest {

    @Mock
    private UserConverter userConverter;
    @Mock
    private UserService userService;
    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Registration rejects the bot name regardless of case and surrounding spaces")
    void registrationRejectsReservedName() {
        for (String name : new String[]{"SERVER_BOT", "server_bot", " Server_Bot "}) {
            assertThrows(UserAlreadyExistsException.class, () ->
                    authService.register(new UserRegistrationRequest(name, null, "secret123")));
        }
        verify(userService, never()).create(any());
    }

    @Test
    @DisplayName("Guest creation rejects the bot name regardless of case")
    void guestCreationRejectsReservedName() {
        assertThrows(UserAlreadyExistsException.class, () -> authService.createGuest("server_bot"));
        verify(userService, never()).create(any());
    }
}
