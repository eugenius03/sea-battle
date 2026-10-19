package com.chnu.seabattle.repository.redis;

import com.chnu.seabattle.enums.EmailTokenPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class EmailRateLimitRedisRepository {

    private static final String KEY_PREFIX = "auth:email-rate:";
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final Duration COOLDOWN = Duration.ofSeconds(30);
    private static final int MAX_PER_WINDOW = 5;

    /**
     * Sliding window over a Sorted Set of send timestamps (score = epoch millis).
     * Drops entries older than the window, then returns how many seconds the caller must wait:
     * the longer of the cooldown since the newest send and, if the window is full, the time until
     * the oldest relevant send leaves the window. Returns 0 when sending is allowed.
     * KEYS: [1] limiter key. ARGV: [1] now (ms), [2] window (ms), [3] max per window, [4] cooldown (ms).
     */
    private static final DefaultRedisScript<Long> RETRY_AFTER_SCRIPT = new DefaultRedisScript<>(
            """
                    local now = tonumber(ARGV[1])
                    local window = tonumber(ARGV[2])
                    local max = tonumber(ARGV[3])
                    local cooldown = tonumber(ARGV[4])
                    redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
                    local count = redis.call('ZCARD', KEYS[1])
                    local wait = 0
                    if count > 0 then
                        local newest = redis.call('ZRANGE', KEYS[1], -1, -1, 'WITHSCORES')
                        wait = math.max(wait, tonumber(newest[2]) + cooldown - now)
                    end
                    if count >= max then
                        local blocking = redis.call('ZRANGE', KEYS[1], count - max, count - max, 'WITHSCORES')
                        wait = math.max(wait, tonumber(blocking[2]) + window - now)
                    end
                    return math.ceil(wait / 1000)
                    """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public Duration retryAfter(EmailTokenPurpose purpose, UUID userId, long nowMillis) {
        Long seconds = redisTemplate.execute(
                RETRY_AFTER_SCRIPT,
                List.of(key(purpose, userId)),
                String.valueOf(nowMillis),
                String.valueOf(WINDOW.toMillis()),
                String.valueOf(MAX_PER_WINDOW),
                String.valueOf(COOLDOWN.toMillis())
        );
        return Duration.ofSeconds(seconds != null ? seconds : 0);
    }

    public void recordSend(EmailTokenPurpose purpose, UUID userId, long nowMillis) {
        String key = key(purpose, userId);
        redisTemplate.opsForZSet().add(key, UUID.randomUUID().toString(), nowMillis);
        redisTemplate.expire(key, WINDOW);
    }

    private static String key(EmailTokenPurpose purpose, UUID userId) {
        return KEY_PREFIX + purpose.name().toLowerCase() + ":" + userId;
    }
}
