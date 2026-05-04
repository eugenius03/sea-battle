package com.chnu.seabattle.service.impl;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.converter.UserConverter;
import com.chnu.seabattle.dto.auth.UserLoginRequest;
import com.chnu.seabattle.dto.auth.UserRegistrationRequest;
import com.chnu.seabattle.entity.User;
import com.chnu.seabattle.exception.BadRequestException;
import com.chnu.seabattle.exception.UnauthorizedException;
import com.chnu.seabattle.exception.UserAlreadyExistsException;
import com.chnu.seabattle.service.AuthService;
import com.chnu.seabattle.service.JwtService;
import com.chnu.seabattle.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
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
        if (userService.existsByUsername(registrationRequest.username())) {
            throw new UserAlreadyExistsException(ErrorConstants.USERNAME_ALREADY_EXISTS);
        }
        if (registrationRequest.password().length() < 6) {
            throw new BadRequestException(ErrorConstants.SHORT_PASSWORD);
        }

        User user = userConverter.toEntity(registrationRequest);
        return userService.create(user);
    }

    @Transactional(readOnly = true)
    @Override
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

}
