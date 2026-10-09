package com.chnu.seabattle.repository.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class TokenBlacklistRedisRepository {

    private static final String KEY_PREFIX = "auth:blacklist:";
    private static final String BLACKLIST_MARKER = "true";

    private final StringRedisTemplate redisTemplate;

    public void blacklist(String jti, Duration ttl) {
        if (!ttl.isNegative() && !ttl.isZero()) {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, BLACKLIST_MARKER, ttl);
        }
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    }
}
