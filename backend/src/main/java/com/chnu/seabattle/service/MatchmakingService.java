package com.chnu.seabattle.service;

import com.chnu.seabattle.dto.ws.MatchmakingStatusResponse;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.repository.redis.MatchmakingQueueRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchmakingService {

    private final MatchmakingQueueRedisRepository queueRepository;
    private final MatchService matchService;
    private final ApplicationEventPublisher eventPublisher;

    public void joinQueue(UUID userId) {
        String opponentIdStr = queueRepository.pairOrEnqueue(userId, System.currentTimeMillis());

        if (opponentIdStr != null) {
            UUID opponentId = UUID.fromString(opponentIdStr);
            notifyMatchFound(userId, opponentId);
        } else {
            Long position = queueRepository.getPosition(userId);
            eventPublisher.publishEvent(new MatchmakingStatusResponse(userId.toString(), position != null ? position : 1L, null));
        }
    }

    public void leaveQueue(UUID userId) {
        queueRepository.remove(userId);
        updateQueuePositions();
    }

    private void notifyMatchFound(UUID player1Id, UUID player2Id) {
        Match match = matchService.createMatch(player1Id, player2Id);
        String inviteToken = match.getInviteToken();
        eventPublisher.publishEvent(new MatchmakingStatusResponse(player1Id.toString(), null, inviteToken));
        eventPublisher.publishEvent(new MatchmakingStatusResponse(player2Id.toString(), null, inviteToken));

        updateQueuePositions();
    }

    private void updateQueuePositions() {
        Set<String> waitingPlayers = queueRepository.getWaitingPlayers();
        long position = 1;
        for (String waitingUserId : waitingPlayers) {
            eventPublisher.publishEvent(new MatchmakingStatusResponse(waitingUserId, position++, null));
        }
    }
}
