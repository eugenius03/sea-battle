package com.chnu.seabattle.controller;

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
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthServiceImpl authService;
    private final JwtServiceImpl jwtServiceImpl;
    private final UserDetailsServiceImpl userDetailsServiceImpl;

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody UserRegistrationRequest registrationRequest,
                                           HttpServletResponse response) {
        try {
            User user = authService.register(registrationRequest);
            response.addHeader(HttpHeaders.SET_COOKIE,
                    authService.createRefreshCookie(user).toString()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "token", jwtServiceImpl.generateAccessToken(
                            userDetailsServiceImpl.convertToUserDetails(user)
                    )
            ).toString());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@Valid @RequestBody UserLoginRequest loginRequest,
                                        HttpServletResponse response) {
        try {
            User user = authService.login(loginRequest);
            response.addHeader(HttpHeaders.SET_COOKIE,
                    authService.createRefreshCookie(user).toString()
            );
            return ResponseEntity.ok().body(Map.of(
                    "token", jwtServiceImpl.generateAccessToken(
                            userDetailsServiceImpl.convertToUserDetails(user)
                    )
            ).toString());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
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
    public ResponseEntity<String> refresh(
            @CookieValue("refreshToken") String refreshToken
    ) {

        try {
            return ResponseEntity.ok().body(Map.of(
                    "token", authService.refresh(refreshToken)
            ).toString());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }
}
