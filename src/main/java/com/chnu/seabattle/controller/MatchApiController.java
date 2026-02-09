package com.chnu.seabattle.controller;

import com.chnu.seabattle.dto.MatchResponse;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.service.GameService;
import com.chnu.seabattle.service.MatchService;
import com.chnu.seabattle.service.WebSocketService;
import com.chnu.seabattle.service.serviceImpl.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchApiController {

    private final MatchService matchService;
    private final UserServiceImpl userServiceImpl;
    private final WebSocketService webSocketService;
    private final GameService gameService;

    @PostMapping("/create")
    public MatchResponse createMatch() {
        UUID playerId = userServiceImpl.findUserFromAuth()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"))
                .getId();
        Match match = matchService.createMatch(playerId);

        return MatchResponse.builder()
                .inviteToken(match.getInviteToken())
                .matchId(match.getId())
                .playerId(playerId)
                .build();
    }

    @PostMapping("/join")
    public MatchResponse joinMatch(
            @RequestParam String inviteToken) {
        Long matchId = matchService.getMatchByInviteToken(inviteToken).getId();
        UUID playerId = userServiceImpl.findUserFromAuth()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"))
                .getId();
        Match match = matchService.joinMatch(playerId, inviteToken);
        UUID opponentId = gameService.getOpponentPlayerId(match, playerId);
        UUID matchPlayerId = gameService.getOpponentPlayerId(match, opponentId);
        webSocketService.handleOpponentConnected(matchId,
                opponentId
        );

        return MatchResponse.builder()
                .matchId(match.getId())
                .playerId(playerId)
                .build();
    }
}

