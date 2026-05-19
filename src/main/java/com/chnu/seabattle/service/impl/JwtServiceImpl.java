package com.chnu.seabattle.service.impl;

import com.chnu.seabattle.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${refresh.token.expiration}")
    private long refreshTokenExpirationInMs;

    @Value("${access.token.expiration}")
    private long accessTokenExpirationInMs;

    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    private String sameSite = "Strict";

    @Override
    public ResponseCookie createRefreshCookie(UserDetails user) {
        String token = generateRefreshToken(user);
        return ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ofMillis(refreshTokenExpirationInMs))
                .build();
    }

    @Override
    public ResponseCookie createAccessCookie(UserDetails user) {
        String token = generateAccessToken(user);
        return ResponseCookie.from("accessToken", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ofMillis(accessTokenExpirationInMs))
                .build();
    }

    public void clearAuthCookies(HttpServletResponse response) {
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE,
                ResponseCookie.from("accessToken", "")
                        .httpOnly(true)
                        .secure(cookieSecure)
                        .sameSite(sameSite)
                        .path("/")
                        .maxAge(0)
                        .build().toString());

        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE,
                ResponseCookie.from("refreshToken", "")
                        .httpOnly(true)
                        .secure(cookieSecure)
                        .sameSite(sameSite)
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
                .setClaims(extraClaims)
                .setId(UUID.randomUUID().toString())
                .setAudience(audience)
                .setSubject(user.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims validateAndExtractClaims(String token, String audience) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .requireAudience(audience)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
