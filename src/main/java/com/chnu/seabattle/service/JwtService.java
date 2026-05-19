package com.chnu.seabattle.service;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.userdetails.UserDetails;

public interface JwtService {
    ResponseCookie createRefreshCookie(UserDetails user);

    ResponseCookie createAccessCookie(UserDetails user);

    void clearAuthCookies(HttpServletResponse response);

    String generateRefreshToken(UserDetails userDetails);

    String generateAccessToken(UserDetails userDetails);

    Claims validateAndExtractClaims(String token, String audience);

}
