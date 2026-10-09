package com.chnu.seabattle.repository.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MatchmakingQueueRedisRepository {

    private static final String QUEUE_KEY = "matchmaking:queue";

    /**
     * Atomically peeks at the front of the Sorted Set (rank 0).
     * If an opponent is waiting and is not the current user: pops and returns the opponent's ID.
     * If no eligible opponent is waiting: adds the current user with score = timestamp (NX prevents resetting wait time) and returns nil.
     */
    private static final DefaultRedisScript<String> PAIR_AND_ENQUEUE_SCRIPT = new DefaultRedisScript<>(
            """
                    local front = redis.call('ZRANGE', KEYS[1], 0, 0)
                    if #front > 0 then
                        local opponent = front[1]
                        if opponent ~= ARGV[1] then
                            redis.call('ZREM', KEYS[1], opponent)
                            return opponent
                        end
                    end
                    redis.call('ZADD', KEYS[1], 'NX', ARGV[2], ARGV[1])
                    return nil
                    """,
            String.class
    );

    private final StringRedisTemplate redisTemplate;

    public String pairOrEnqueue(UUID userId, long timestamp) {
        return redisTemplate.execute(
                PAIR_AND_ENQUEUE_SCRIPT,
                List.of(QUEUE_KEY),
                userId.toString(),
                String.valueOf(timestamp)
        );
    }

    public void remove(UUID userId) {
        redisTemplate.opsForZSet().remove(QUEUE_KEY, userId.toString());
    }

    public Long getPosition(UUID userId) {
        Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, userId.toString());
        return rank != null ? rank + 1 : null;
    }

    public Set<String> getWaitingPlayers() {
        Set<String> players = redisTemplate.opsForZSet().range(QUEUE_KEY, 0, -1);
        return players != null ? players : Set.of();
    }

    public Long getQueueSize() {
        return redisTemplate.opsForZSet().size(QUEUE_KEY);
    }
}
