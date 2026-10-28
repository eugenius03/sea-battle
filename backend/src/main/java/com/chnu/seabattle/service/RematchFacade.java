package com.chnu.seabattle.service;

import com.chnu.seabattle.dto.match.MatchInviteResponse;
import com.chnu.seabattle.entity.Match;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RematchFacade {

    private final MatchService matchService;
    private final GameBotService gameBotService;

    public Optional<MatchInviteResponse> rematch(String inviteToken, UUID userId) {
        if (matchService.isBotRematch(inviteToken, userId)) {
            Match botMatch = gameBotService.createBotMatch(userId);
            return Optional.of(new MatchInviteResponse(botMatch.getInviteToken(), userId));
        }
        return matchService.processRematch(inviteToken, userId);
    }
}
