package com.chnu.seabattle.service.serviceImpl;

import com.chnu.seabattle.converter.UserConverter;
import com.chnu.seabattle.dto.UserLoginRequest;
import com.chnu.seabattle.dto.UserRegistrationRequest;
import com.chnu.seabattle.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl {

    private final UserServiceImpl userServiceImpl;
    private final JwtServiceImpl jwtServiceImpl;
    private final UserDetailsServiceImpl userDetailsService;
    private final UserConverter userConverter;

    @Transactional
    public User register(@Valid UserRegistrationRequest registrationRequest) {
        if (userServiceImpl.existsByUsername(registrationRequest.getUsername())) {
            throw new IllegalArgumentException("Username already exists!");
        }

        User user = userConverter.toEntity(registrationRequest);
        return userServiceImpl.create(user);
    }

    @Transactional(readOnly = true)
    public User login(@Valid UserLoginRequest loginRequest) {

        if (userServiceImpl.existsByUsername(loginRequest.getUsername())) {
            User user = userServiceImpl.findByUsername(loginRequest.getUsername()).get();
            if (userServiceImpl.checkPassword(loginRequest.getPassword(), user.getPasswordHash())) {
                return user;
            } else {
                throw new IllegalArgumentException("Invalid password");
            }
        } else {
            throw new IllegalArgumentException("Invalid username");
        }
    }


    public ResponseCookie createRefreshCookie(User user) {
        String token = jwtServiceImpl.generateRefreshToken(
                userDetailsService.loadUserByUsername(user.getUsername())
        );
        return ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(false)    // For development
                .sameSite("Lax")    // CSRF protection
                .path("/")
                .maxAge(Duration.ofDays(7))
                .build();
    }

    public ResponseCookie createAccessCookie(User user) {
        String token = jwtServiceImpl.generateAccessToken(
                userDetailsService.loadUserByUsername(user.getUsername())
        );
        return ResponseCookie.from("accessToken", token)
                .httpOnly(true)
                .secure(false)    // For development
                .sameSite("Lax")    // CSRF protection
                .path("/")
                .maxAge(Duration.ofDays(7))
                .build();
    }

    public String refresh(String refreshToken) throws Exception {

        if (jwtServiceImpl.isTokenExpired(refreshToken)) {
            throw new Exception("Refresh token expired");
        }

        String username = jwtServiceImpl.getUsernameFromToken(refreshToken);
        User user = userServiceImpl.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        return jwtServiceImpl.generateAccessToken(
                userDetailsService.convertToUserDetails(user)
        );
    }
}
