package com.chnu.seabattle.repository.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TokenBlacklistRedisRepository {

    private static final String KEY_PREFIX = "auth:blacklist:";
    private static final String BLACKLIST_MARKER = "true";
    private static final String REVOKED_BEFORE_KEY_PREFIX = "auth:revoked-before:";

    private final StringRedisTemplate redisTemplate;

    public void blacklist(String jti, Duration ttl) {
        if (!ttl.isNegative() && !ttl.isZero()) {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, BLACKLIST_MARKER, ttl);
        }
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    }

    public void revokeAllBefore(String username, Instant cutoff, Duration ttl) {
        if (!ttl.isNegative() && !ttl.isZero()) {
            redisTemplate.opsForValue().set(
                    REVOKED_BEFORE_KEY_PREFIX + username,
                    String.valueOf(cutoff.getEpochSecond()),
                    ttl
            );
        }
    }

    public Optional<Instant> getRevokedBefore(String username) {
        String value = redisTemplate.opsForValue().get(REVOKED_BEFORE_KEY_PREFIX + username);
        return Optional.ofNullable(value).map(seconds -> Instant.ofEpochSecond(Long.parseLong(seconds)));
    }
}
