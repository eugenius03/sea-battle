package com.chnu.seabattle.repository.redis;

import com.chnu.seabattle.enums.EmailTokenPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class EmailTokenRedisRepository {

    private static final String KEY_PREFIX = "auth:email-token:";

    /**
     * Atomically replaces the user's current token for one purpose.
     * Deletes the previous token key (found through the user pointer), then stores the new
     * token hash -> userId and the user -> current hash pointer, both with the same TTL.
     * KEYS: [1] user pointer key, [2] new token key.
     * ARGV: [1] token key prefix, [2] userId, [3] token hash, [4] TTL in seconds.
     */
    private static final DefaultRedisScript<Long> ISSUE_SCRIPT = new DefaultRedisScript<>(
            """
                    local old = redis.call('GET', KEYS[1])
                    if old then
                        redis.call('DEL', ARGV[1] .. old)
                    end
                    redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[4])
                    redis.call('SET', KEYS[1], ARGV[3], 'EX', ARGV[4])
                    return 1
                    """,
            Long.class
    );

    /**
     * Atomically consumes a token: GETDEL on the token key, then removes the user pointer
     * only if it still points at this hash. Returns the userId, or nil if the token is unknown.
     * KEYS: [1] token key. ARGV: [1] user pointer key prefix, [2] token hash.
     */
    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>(
            """
                    local userId = redis.call('GETDEL', KEYS[1])
                    if not userId then
                        return nil
                    end
                    local userKey = ARGV[1] .. userId
                    if redis.call('GET', userKey) == ARGV[2] then
                        redis.call('DEL', userKey)
                    end
                    return userId
                    """,
            String.class
    );

    private final StringRedisTemplate redisTemplate;

    public void issue(EmailTokenPurpose purpose, UUID userId, String tokenHash, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("TTL must be positive");
        }
        redisTemplate.execute(
                ISSUE_SCRIPT,
                List.of(userKey(purpose, userId.toString()), tokenKey(purpose, tokenHash)),
                tokenKeyPrefix(purpose),
                userId.toString(),
                tokenHash,
                String.valueOf(ttl.toSeconds())
        );
    }

    public Optional<UUID> consume(EmailTokenPurpose purpose, String tokenHash) {
        String userId = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(tokenKey(purpose, tokenHash)),
                userKeyPrefix(purpose),
                tokenHash
        );
        return Optional.ofNullable(userId).map(UUID::fromString);
    }

    private static String tokenKeyPrefix(EmailTokenPurpose purpose) {
        return KEY_PREFIX + purpose.name().toLowerCase() + ":token:";
    }

    private static String userKeyPrefix(EmailTokenPurpose purpose) {
        return KEY_PREFIX + purpose.name().toLowerCase() + ":user:";
    }

    private static String tokenKey(EmailTokenPurpose purpose, String tokenHash) {
        return tokenKeyPrefix(purpose) + tokenHash;
    }

    private static String userKey(EmailTokenPurpose purpose, String userId) {
        return userKeyPrefix(purpose) + userId;
    }
}
