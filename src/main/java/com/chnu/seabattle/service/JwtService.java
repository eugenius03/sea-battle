package com.chnu.seabattle.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${refresh.token.expiration}")
    private long refreshTokenExpirationInMs;

    @Value("${access.token.expiration}")
    private long accessTokenExpirationInMs;

    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    private static final String SAME_SITE = "Strict";

    public ResponseCookie createRefreshCookie(UserDetails user) {
        String token = generateRefreshToken(user);
        return ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(SAME_SITE)
                .path("/")
                .maxAge(Duration.ofMillis(refreshTokenExpirationInMs))
                .build();
    }

    public ResponseCookie createAccessCookie(UserDetails user) {
        String token = generateAccessToken(user);
        return ResponseCookie.from("accessToken", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(SAME_SITE)
                .path("/")
                .maxAge(Duration.ofMillis(accessTokenExpirationInMs))
                .build();
    }

    public void clearAuthCookies(HttpServletResponse response) {
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE,
                ResponseCookie.from("accessToken", "")
                        .httpOnly(true)
                        .secure(cookieSecure)
                        .sameSite(SAME_SITE)
                        .path("/")
                        .maxAge(0)
                        .build().toString());

        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE,
                ResponseCookie.from("refreshToken", "")
                        .httpOnly(true)
                        .secure(cookieSecure)
                        .sameSite(SAME_SITE)
                        .path("/")
                        .maxAge(0)
                        .build().toString());

        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE,
                ResponseCookie.from("JSESSIONID", "")
                        .path("/")
                        .maxAge(0)
                        .build().toString());
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return generateToken(userDetails, "refresh", refreshTokenExpirationInMs);
    }

    public String generateAccessToken(UserDetails userDetails) {

        return generateToken(userDetails, "access", accessTokenExpirationInMs);
    }

    private String generateToken(
            UserDetails userDetails,
            String audience,
            long expirationMs) {
        Map<String, Object> claims = new HashMap<>();
        return generateToken(claims, audience, userDetails, expirationMs);
    }

    private String generateToken(Map<String, Object> extraClaims, String audience, UserDetails user, long expirationMs) {
        return Jwts.builder()
                .claims(extraClaims)
                .id(UUID.randomUUID().toString())
                .audience().add(audience).and()
                .subject(user.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims validateAndExtractClaims(String token, String audience) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}