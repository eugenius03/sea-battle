package com.chnu.seabattle.service;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.entity.RevokedToken;
import com.chnu.seabattle.exception.UnauthorizedException;
import com.chnu.seabattle.repository.RevokedTokenRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RevokedTokenRepository revokedTokenRepository;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public boolean revokedTokenExists(String jti) {
        return revokedTokenRepository.existsById(UUID.fromString(jti));
    }

    public void revokeToken(String jti, Instant expiresAt) {
        try {
            revokedTokenRepository.save(new RevokedToken(UUID.fromString(jti), expiresAt));
        } catch (DataIntegrityViolationException e) {
            throw new UnauthorizedException(ErrorConstants.INVALID_TOKEN);
        }
    }

    @Transactional
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
