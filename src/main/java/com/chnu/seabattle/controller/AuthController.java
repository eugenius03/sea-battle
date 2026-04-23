package com.chnu.seabattle.controller;

import com.chnu.seabattle.dto.UserLoginRequest;
import com.chnu.seabattle.dto.UserRegistrationRequest;
import com.chnu.seabattle.entity.User;
import com.chnu.seabattle.service.AuthService;
import com.chnu.seabattle.service.JwtService;
import com.chnu.seabattle.service.impl.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserDetailsServiceImpl userDetailsServiceImpl;
    private final JwtService jwtService;

    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

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
        for (String name : new String[]{"accessToken", "refreshToken"}) {
            response.addHeader(HttpHeaders.SET_COOKIE,
                    ResponseCookie.from(name, "")
                            .httpOnly(true)
                            .secure(cookieSecure)
                            .sameSite("Strict")
                            .path("/")
                            .maxAge(0)
                            .build().toString());
        }
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
