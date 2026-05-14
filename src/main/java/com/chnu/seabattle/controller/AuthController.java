package com.chnu.seabattle.controller;

import com.chnu.seabattle.dto.auth.UserLoginRequest;
import com.chnu.seabattle.dto.auth.UserRegistrationRequest;
import com.chnu.seabattle.dto.auth.UserResponse;
import com.chnu.seabattle.entity.User;
import com.chnu.seabattle.service.AuthService;
import com.chnu.seabattle.service.JwtService;
import com.chnu.seabattle.service.UserService;
import com.chnu.seabattle.service.impl.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final UserDetailsServiceImpl userDetailsServiceImpl;
    private final JwtService jwtService;

    @GetMapping("/me")
    public UserResponse getCurrentUser() {
        User user = userService.getAuthenticatedUser();

        return new UserResponse(user.getId(), user.getUsername());
    }

    @PostMapping("/register")
    public void register(@Valid @RequestBody UserRegistrationRequest registrationRequest,
                         HttpServletResponse response) {
        User user = authService.register(registrationRequest);
        UserDetails userDetails = userDetailsServiceImpl.convertToUserDetails(user);
        response.addHeader(HttpHeaders.SET_COOKIE,
                jwtService.createRefreshCookie(userDetails).toString()
        );
        response.addHeader(HttpHeaders.SET_COOKIE,
                jwtService.createAccessCookie(userDetails).toString());
    }

    @PostMapping("/login")
    public void login(@Valid @RequestBody UserLoginRequest loginRequest,
                      HttpServletResponse response) {
        UserDetails user = authService.login(loginRequest);
        response.addHeader(HttpHeaders.SET_COOKIE,
                jwtService.createRefreshCookie(user).toString()
        );
        response.addHeader(HttpHeaders.SET_COOKIE,
                jwtService.createAccessCookie(user).toString());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        jwtService.clearAuthCookies(response);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh")
    public void refresh(
            @CookieValue("refreshToken") String refreshToken,
            HttpServletResponse response
    ) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                authService.refresh(refreshToken).toString());
    }
}