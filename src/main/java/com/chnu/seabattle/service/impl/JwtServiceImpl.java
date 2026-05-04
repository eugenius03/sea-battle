package com.chnu.seabattle.service.impl;

import com.chnu.seabattle.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtServiceImpl implements JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${refresh.token.expiration}")
    private long refreshTokenExpirationInMs;

    @Value("${access.token.expiration}")
    private long accessTokenExpirationInMs;

    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    public String getUsernameFromToken(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    @Override
    public ResponseCookie createRefreshCookie(UserDetails user) {
        String token = generateRefreshToken(user);
        return ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
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
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofMillis(accessTokenExpirationInMs))
                .build();
    }

    public void clearAuthCookies(HttpServletResponse response) {
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE,
                ResponseCookie.from("accessToken", "")
                        .httpOnly(true)
                        .secure(cookieSecure)
                        .sameSite("Strict")
                        .path("/")
                        .maxAge(0)
                        .build().toString());

        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE,
                ResponseCookie.from("refreshToken", "")
                        .httpOnly(true)
                        .secure(cookieSecure)
                        .sameSite("Strict")
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
        return generateToken(userDetails, refreshTokenExpirationInMs);
    }

    public String generateAccessToken(UserDetails userDetails) {

        return generateToken(userDetails, accessTokenExpirationInMs);
    }

    private String generateToken(
            UserDetails userDetails,
            long expirationMs) {
        Map<String, Object> claims = new HashMap<>();
        return generateToken(claims, userDetails, expirationMs);
    }

    private String generateToken(Map<String, Object> extraClaims, UserDetails user, long expirationMs) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(user.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (JwtException e) {
            return true;
        }
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        if (!isTokenExpired(token)) {
            final String email = getUsernameFromToken(token);
            return (email.equals(userDetails.getUsername()));
        }
        return false;
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolvers) {
        final Claims claims = extractAllClaims(token);
        return claimsResolvers.apply(claims);
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
