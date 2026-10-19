package com.chnu.seabattle.service;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.exception.UnauthorizedException;
import com.chnu.seabattle.repository.redis.TokenBlacklistRedisRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final TokenBlacklistRedisRepository tokenBlacklistRedisRepository;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Value("${refresh.token.expiration}")
    private long refreshTokenExpirationInMs;

    public boolean revokedTokenExists(String jti) {
        return tokenBlacklistRedisRepository.isBlacklisted(jti);
    }

    public void revokeToken(String jti, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        tokenBlacklistRedisRepository.blacklist(jti, ttl);
    }

    public void revokeAllForUser(String username) {
        tokenBlacklistRedisRepository.revokeAllBefore(username, Instant.now(), Duration.ofMillis(refreshTokenExpirationInMs));
    }

    public UserDetails validateAndRotateRefreshToken(String refreshToken) {
        Claims claims = jwtService.validateAndExtractClaims(refreshToken, "refresh");

        if (revokedTokenExists(claims.getId())) {
            throw new UnauthorizedException(ErrorConstants.INVALID_TOKEN);
        }

        boolean issuedBeforeCutoff = tokenBlacklistRedisRepository.getRevokedBefore(claims.getSubject())
                .map(cutoff -> claims.getIssuedAt().toInstant().getEpochSecond() < cutoff.getEpochSecond())
                .orElse(false);
        if (issuedBeforeCutoff) {
            throw new UnauthorizedException(ErrorConstants.INVALID_TOKEN);
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(claims.getSubject());
        if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
            throw new UnauthorizedException(ErrorConstants.INVALID_TOKEN);
        }

        revokeToken(claims.getId(), claims.getExpiration().toInstant());
        return userDetails;
    }
}
