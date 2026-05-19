package com.chnu.seabattle.service.impl;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.entity.RevokedToken;
import com.chnu.seabattle.exception.UnauthorizedException;
import com.chnu.seabattle.repository.RevokedTokenRepository;
import com.chnu.seabattle.service.JwtService;
import com.chnu.seabattle.service.RefreshTokenService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {
    private final RevokedTokenRepository revokedTokenRepository;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public boolean revokedTokenExists(String jti) {
        return revokedTokenRepository.existsById(UUID.fromString(jti));
    }

    @Override
    public void revokeToken(String jti, Instant expiresAt) {
        revokedTokenRepository.save(new RevokedToken(UUID.fromString(jti), expiresAt));
    }

    @Override
    public UserDetails validateAndRotateRefreshToken(String refreshToken) {
        Claims claims = jwtService.validateAndExtractClaims(refreshToken, "refresh");

        if (revokedTokenExists(claims.getId())) {
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
