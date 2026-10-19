package com.chnu.seabattle.controller;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.dto.auth.EmailRequest;
import com.chnu.seabattle.dto.auth.GuestRequest;
import com.chnu.seabattle.dto.auth.PasswordResetConfirmRequest;
import com.chnu.seabattle.dto.auth.TokenRequest;
import com.chnu.seabattle.dto.auth.UserLoginRequest;
import com.chnu.seabattle.dto.auth.UserRegistrationRequest;
import com.chnu.seabattle.entity.User;
import com.chnu.seabattle.enums.TokenType;
import com.chnu.seabattle.exception.UnauthorizedException;
import com.chnu.seabattle.service.AuthService;
import com.chnu.seabattle.service.JwtService;
import com.chnu.seabattle.service.RefreshTokenService;
import com.chnu.seabattle.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
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
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/register")
    public void register(@Valid @RequestBody UserRegistrationRequest registrationRequest,
                         HttpServletResponse response) {
        User user = authService.register(registrationRequest);
        UserDetails userDetails = userService.toUserDetails(user);
        response.addHeader(HttpHeaders.SET_COOKIE, jwtService.createCookie(userDetails, TokenType.ACCESS).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, jwtService.createCookie(userDetails, TokenType.REFRESH).toString());
    }

    @PostMapping("/guest")
    public void guest(@Valid @RequestBody GuestRequest request, HttpServletResponse response) {
        User user = authService.createGuest(request.username());
        UserDetails userDetails = userService.toUserDetails(user);
        response.addHeader(HttpHeaders.SET_COOKIE, jwtService.createCookie(userDetails, TokenType.ACCESS).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, jwtService.createCookie(userDetails, TokenType.REFRESH).toString());

    }

    @PostMapping("/login")
    public void login(@Valid @RequestBody UserLoginRequest loginRequest,
                      HttpServletResponse response) {
        UserDetails userDetails = authService.login(loginRequest);
        response.addHeader(HttpHeaders.SET_COOKIE, jwtService.createCookie(userDetails, TokenType.ACCESS).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, jwtService.createCookie(userDetails, TokenType.REFRESH).toString());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        jwtService.clearAuthCookies(response);
        if (refreshToken != null) {
            try {
                Claims claims = jwtService.validateAndExtractClaims(refreshToken, "refresh");
                refreshTokenService.revokeToken(claims.getId(), claims.getExpiration().toInstant());
            } catch (JwtException e) {
                log.warn("Invalid token on logout: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh")
    public void refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        if (refreshToken == null) {
            throw new UnauthorizedException(ErrorConstants.INVALID_TOKEN);
        }
        UserDetails userDetails = refreshTokenService.validateAndRotateRefreshToken(refreshToken);

        log.info("Refreshed access token for user: {}", userDetails.getUsername());
        response.addHeader(HttpHeaders.SET_COOKIE, jwtService.createCookie(userDetails, TokenType.ACCESS).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, jwtService.createCookie(userDetails, TokenType.REFRESH).toString());
    }

    @PostMapping("/email")
    public void requestEmail(@Valid @RequestBody EmailRequest request) {
        userService.requestEmailVerification(userService.getAuthenticatedUser(), request.email());
    }

    @PostMapping("/verify-email")
    public void verifyEmail(@Valid @RequestBody TokenRequest request) {
        userService.verifyEmail(request.token());
    }

    @PostMapping("/password-reset")
    public void requestPasswordReset(@Valid @RequestBody EmailRequest request) {
        userService.requestResetPassword(request.email());
    }

    @PostMapping("/password-reset/confirm")
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        userService.resetPassword(request.token(), request.password());
    }
}
