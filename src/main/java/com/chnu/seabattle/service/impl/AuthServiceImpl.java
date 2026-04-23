package com.chnu.seabattle.service.impl;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.converter.UserConverter;
import com.chnu.seabattle.dto.UserLoginRequest;
import com.chnu.seabattle.dto.UserRegistrationRequest;
import com.chnu.seabattle.entity.User;
import com.chnu.seabattle.exception.ResourceNotFoundException;
import com.chnu.seabattle.exception.UnauthorizedException;
import com.chnu.seabattle.exception.UserAlreadyExistsException;
import com.chnu.seabattle.service.AuthService;
import com.chnu.seabattle.service.JwtService;
import com.chnu.seabattle.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final UserConverter userConverter;
    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    @Override
    public User register(@Valid UserRegistrationRequest registrationRequest) {
        if (userService.existsByUsername(registrationRequest.getUsername())) {
            throw new UserAlreadyExistsException(ErrorConstants.USERNAME_ALREADY_EXISTS);
        }

        User user = userConverter.toEntity(registrationRequest);
        return userService.create(user);
    }

    @Transactional(readOnly = true)
    @Override
    public UserDetails login(@Valid UserLoginRequest loginRequest) {

        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        return (UserDetails) auth.getPrincipal();
    }


    @Override
    public ResponseCookie refresh(String refreshToken) {

        if (jwtService.isTokenExpired(refreshToken)) {
            throw new UnauthorizedException(ErrorConstants.REFRESH_TOKEN_EXPIRED);
        }

        String username = jwtService.getUsernameFromToken(refreshToken);
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException(ErrorConstants.INVALID_TOKEN));

        return jwtService.createAccessCookie(userDetailsService.convertToUserDetails(user));
    }

    @Override
    public User getAuthenticatedUser() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException(ErrorConstants.USER_NOT_AUTHENTICATED);
        }

        String username = authentication.getName();
        return userService.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorConstants.AUTHENTICATED_USER_NOT_FOUND
                ));

    }
}
