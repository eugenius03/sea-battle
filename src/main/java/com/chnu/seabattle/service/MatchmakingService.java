package com.chnu.seabattle.service;

import com.chnu.seabattle.entity.Match;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchmakingService {

    private final StringRedisTemplate redisTemplate;
    private final MatchService matchService;
    private final WebSocketService webSocketService;

    private static final String QUEUE_KEY = "matchmaking:queue";

    public void joinQueue(UUID userId) {
        UUID opponentId = Optional.of(redisTemplate.opsForList().rightPop(QUEUE_KEY))
                .map(UUID::fromString)
                .orElse(null);

        if (opponentId != null) {
            if (opponentId.equals(userId)) {
                redisTemplate.opsForList().rightPush(QUEUE_KEY, userId.toString());
                Long position = redisTemplate.opsForList().size(QUEUE_KEY);
                webSocketService.sendQueuePosition(userId.toString(), position);
                return;
            }
            notifyMatchFound(userId, opponentId);
        } else {
            redisTemplate.opsForList().rightPush(QUEUE_KEY, userId.toString());

            Long position = redisTemplate.opsForList().size(QUEUE_KEY);
            webSocketService.sendQueuePosition(userId.toString(), position);
        }
    }

    public void leaveQueue(UUID userId) {
        redisTemplate.opsForList().remove(QUEUE_KEY, 0, userId.toString());

        Thread.startVirtualThread(this::updateQueuePosition);
    }

    private void notifyMatchFound(UUID player1Id, UUID player2Id) {
        Match match = matchService.createMatch(player1Id, player2Id);
        String inviteToken = match.getInviteToken();
        webSocketService.sendMatchFound(player1Id.toString(), inviteToken);
        webSocketService.sendMatchFound(player2Id.toString(), inviteToken);

        Thread.startVirtualThread(this::updateQueuePosition);
    }

    private void updateQueuePosition() {
        java.util.List<String> usersInQueue = redisTemplate.opsForList().range(QUEUE_KEY, 0, -1);
        if (usersInQueue != null) {
            for (int i = 0; i < usersInQueue.size(); i++) {
                webSocketService.sendQueuePosition(usersInQueue.get(i), (long) (i + 1));
            }
        }
    }
}
