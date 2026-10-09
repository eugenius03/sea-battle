package com.chnu.seabattle.service;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.constants.NumericalConstants;
import com.chnu.seabattle.converter.UserConverter;
import com.chnu.seabattle.dto.auth.UserLoginRequest;
import com.chnu.seabattle.dto.auth.UserRegistrationRequest;
import com.chnu.seabattle.entity.User;
import com.chnu.seabattle.exception.UserAlreadyExistsException;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserConverter userConverter;
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public User register(@Valid UserRegistrationRequest registrationRequest) {
        if (userService.existsByUsername(registrationRequest.username())) {
            log.warn("Failed registration: Username {} already exists.", registrationRequest.username());
            throw new UserAlreadyExistsException(ErrorConstants.USERNAME_ALREADY_EXISTS);
        }
        if (registrationRequest.email() != null && userService.existsByEmail(registrationRequest.email())) {
            log.warn("Failed registration: Email {} already exists.", registrationRequest.email());
            throw new UserAlreadyExistsException(ErrorConstants.EMAIL_ALREADY_EXISTS);
        }

        User user = userConverter.toEntity(registrationRequest);
        return userService.create(user);
    }

    @Transactional(readOnly = true)
    public UserDetails login(@Valid UserLoginRequest loginRequest) {

        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.username(),
                        loginRequest.password()
                )
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        return (UserDetails) auth.getPrincipal();
    }

    @Transactional
    public User createGuest(@NonNull String username) {
        String baseName = username.trim();
        String proposedUsername = null;

        for (int i = 0; i < NumericalConstants.MAX_GUEST_USERNAME_RETRIES; i++) {
            String candidate = baseName + "_" + random.nextInt(
                    NumericalConstants.GUEST_SUFFIX_MIN,
                    NumericalConstants.GUEST_SUFFIX_MAX
            );
            if (!userService.existsByUsername(candidate)) {
                proposedUsername = candidate;
                break;
            }
        }
        if (proposedUsername == null) {
            proposedUsername = baseName + "_" + random.nextInt(
                    NumericalConstants.GUEST_FALLBACK_SUFFIX_MIN,
                    NumericalConstants.GUEST_FALLBACK_SUFFIX_MAX
            );
        }
        User user = User.builder()
                .username(proposedUsername)
                .isGuest(true)
                .build();
        return userService.create(user);
    }

}
