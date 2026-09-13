package com.chnu.seabattle.service;

import com.chnu.seabattle.entity.Match;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchmakingService {

    private final StringRedisTemplate redisTemplate;
    private final MatchService matchService;
    private final WebSocketService webSocketService;

    private static final String QUEUE_KEY = "matchmaking:queue";

    /**
     * Atomically peeks at the front of the queue and pops the opponent only if they are
     * a different player. Returns the opponent's ID string, or null if the queue is empty
     * or the only entry is the requesting player themselves.
     * <p>
     * This Lua script runs as a single Redis command, eliminating the race condition.
     */
    private static final DefaultRedisScript<String> PAIR_SCRIPT = new DefaultRedisScript<>(
            """
                    local front = redis.call('LINDEX', KEYS[1], 0)
                    if front == false then
                        return nil
                    end
                    if front ~= ARGV[1] then
                        redis.call('LPOP', KEYS[1])
                        return front
                    end
                    return nil
                    """,
            String.class
    );

    public void joinQueue(UUID userId) {
        String opponentIdStr = redisTemplate.execute(
                PAIR_SCRIPT,
                List.of(QUEUE_KEY),
                userId.toString()
        );

        if (opponentIdStr != null) {
            UUID opponentId = UUID.fromString(opponentIdStr);
            notifyMatchFound(userId, opponentId);
        } else {
            // Only push if the user isn't already in the queue
            boolean alreadyQueued = redisTemplate.opsForList()
                    .range(QUEUE_KEY, 0, -1)
                    .contains(userId.toString());
            if (!alreadyQueued) {
                redisTemplate.opsForList().rightPush(QUEUE_KEY, userId.toString());
            }
            Long position = redisTemplate.opsForList().size(QUEUE_KEY);
            webSocketService.sendQueuePosition(userId.toString(), position);
        }
    }

    public void leaveQueue(UUID userId) {
        redisTemplate.opsForList().remove(QUEUE_KEY, 0, userId.toString());
        updateQueuePosition();
    }

    private void notifyMatchFound(UUID player1Id, UUID player2Id) {
        Match match = matchService.createMatch(player1Id, player2Id);
        String inviteToken = match.getInviteToken();
        webSocketService.sendMatchFound(player1Id.toString(), inviteToken);
        webSocketService.sendMatchFound(player2Id.toString(), inviteToken);
        updateQueuePosition();
    }

    private void updateQueuePosition() {
        List<String> usersInQueue = redisTemplate.opsForList().range(QUEUE_KEY, 0, -1);
        if (usersInQueue != null) {
            for (int i = 0; i < usersInQueue.size(); i++) {
                webSocketService.sendQueuePosition(usersInQueue.get(i), (long) (i + 1));
            }
        }
    }
}
