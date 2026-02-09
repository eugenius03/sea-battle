package com.chnu.seabattle.controller;

import com.chnu.seabattle.dto.AuthResponse;
import com.chnu.seabattle.dto.UserLoginRequest;
import com.chnu.seabattle.dto.UserRegistrationRequest;
import com.chnu.seabattle.entity.User;
import com.chnu.seabattle.service.serviceImpl.AuthServiceImpl;
import com.chnu.seabattle.service.serviceImpl.JwtServiceImpl;
import com.chnu.seabattle.service.serviceImpl.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthServiceImpl authService;
    private final JwtServiceImpl jwtServiceImpl;
    private final UserDetailsServiceImpl userDetailsServiceImpl;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody UserRegistrationRequest registrationRequest,
                                                 HttpServletResponse response) {
        User user = authService.register(registrationRequest);
        response.addHeader(HttpHeaders.SET_COOKIE,
                authService.createRefreshCookie(user).toString()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(
                AuthResponse.builder()
                        .token(jwtServiceImpl.generateAccessToken(
                                userDetailsServiceImpl.convertToUserDetails(user))
                        )
                        .build()
        );
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody UserLoginRequest loginRequest,
                              HttpServletResponse response) {
        User user = authService.login(loginRequest);
        response.addHeader(HttpHeaders.SET_COOKIE,
                authService.createRefreshCookie(user).toString()
        );
        return AuthResponse.builder()
                .token(jwtServiceImpl.generateAccessToken(
                        userDetailsServiceImpl.convertToUserDetails(user))
                ).build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {

        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false)    // For development
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok().build();

    }

    @PostMapping("/refresh")
    public AuthResponse refresh(
            @CookieValue("refreshToken") String refreshToken
    ) {
        return AuthResponse.builder()
                .token(authService.refresh(refreshToken))
                .build();
    }
}
