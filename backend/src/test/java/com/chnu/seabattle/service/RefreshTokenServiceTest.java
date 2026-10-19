package com.chnu.seabattle.service;

import com.chnu.seabattle.exception.UnauthorizedException;
import com.chnu.seabattle.repository.redis.TokenBlacklistRedisRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService revoke-all-before cutoff")
class RefreshTokenServiceTest {

    private static final String REFRESH_AUDIENCE = "refresh";
    private static final String TOKEN = "refresh-token";
    private static final String USERNAME = "alice";
    private static final String JTI = "jti-1";

    @Mock
    private TokenBlacklistRedisRepository tokenBlacklistRedisRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private Claims claims;
    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpirationInMs", 604_800_000L);
        lenient().when(jwtService.validateAndExtractClaims(TOKEN, REFRESH_AUDIENCE)).thenReturn(claims);
        lenient().when(claims.getId()).thenReturn(JTI);
        lenient().when(claims.getSubject()).thenReturn(USERNAME);
        lenient().when(tokenBlacklistRedisRepository.isBlacklisted(JTI)).thenReturn(false);
    }

    @Test
    @DisplayName("rejects a token issued before the user's cutoff")
    void rejectsTokenIssuedBeforeCutoff() {
        Instant cutoff = Instant.parse("2026-10-18T12:00:00Z");
        when(claims.getIssuedAt()).thenReturn(Date.from(cutoff.minusSeconds(60)));
        when(tokenBlacklistRedisRepository.getRevokedBefore(USERNAME)).thenReturn(Optional.of(cutoff));

        assertThrows(UnauthorizedException.class, () -> refreshTokenService.validateAndRotateRefreshToken(TOKEN));
    }

    @Test
    @DisplayName("accepts a token issued after the user's cutoff")
    void acceptsTokenIssuedAfterCutoff() {
        Instant cutoff = Instant.parse("2026-10-18T12:00:00Z");
        stubValidUser(Date.from(cutoff.plusSeconds(60)), Optional.of(cutoff));

        assertSame(userDetails, refreshTokenService.validateAndRotateRefreshToken(TOKEN));
    }

    @Test
    @DisplayName("accepts a token issued in the same second as the cutoff")
    void acceptsTokenIssuedInCutoffSecond() {
        Instant cutoff = Instant.parse("2026-10-18T12:00:00Z");
        stubValidUser(Date.from(cutoff), Optional.of(cutoff));

        assertSame(userDetails, refreshTokenService.validateAndRotateRefreshToken(TOKEN));
    }

    @Test
    @DisplayName("accepts any token when the user has no cutoff")
    void acceptsTokenWhenNoCutoff() {
        stubValidUser(new Date(), Optional.empty());

        assertSame(userDetails, refreshTokenService.validateAndRotateRefreshToken(TOKEN));
    }

    @Test
    @DisplayName("revokeAllForUser stores a cutoff with the refresh-token lifetime as TTL")
    void revokeAllForUserStoresCutoff() {
        refreshTokenService.revokeAllForUser(USERNAME);

        verify(tokenBlacklistRedisRepository)
                .revokeAllBefore(eq(USERNAME), any(Instant.class), eq(Duration.ofMillis(604_800_000L)));
    }

    private void stubValidUser(Date issuedAt, Optional<Instant> cutoff) {
        UserDetails details = userDetails;
        lenient().when(claims.getIssuedAt()).thenReturn(issuedAt);
        when(claims.getExpiration()).thenReturn(Date.from(Instant.now().plusSeconds(3600)));
        when(tokenBlacklistRedisRepository.getRevokedBefore(USERNAME)).thenReturn(cutoff);
        when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(details);
        when(details.isEnabled()).thenReturn(true);
        when(details.isAccountNonLocked()).thenReturn(true);
    }
}
