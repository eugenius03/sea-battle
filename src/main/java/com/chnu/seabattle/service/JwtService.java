package com.chnu.seabattle.service;

import org.springframework.http.ResponseCookie;
import org.springframework.security.core.userdetails.UserDetails;

public interface JwtService {

    String getUsernameFromToken(String token);

    ResponseCookie createRefreshCookie(UserDetails user);

    ResponseCookie createAccessCookie(UserDetails user);

    String generateRefreshToken(UserDetails userDetails);

    String generateAccessToken(UserDetails userDetails);

    boolean isTokenExpired(String token);

    boolean isTokenValid(String token, UserDetails userDetails);
}
