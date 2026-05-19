package com.chnu.seabattle.service;

import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;

public interface RefreshTokenService {

    boolean revokedTokenExists(String jti);

    void revokeToken(String jti, Instant expiresAt);

    UserDetails validateAndRotateRefreshToken(String refreshToken);

}
