package com.chnu.seabattle.controller;

import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.exception.GameRuleViolationException;
import com.chnu.seabattle.service.GameService;
import com.chnu.seabattle.service.MatchService;
import com.chnu.seabattle.service.WebSocketService;
import com.chnu.seabattle.service.serviceImpl.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
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
    public ResponseEntity<?> createMatch() {
        try {
            UUID playerId = userServiceImpl.findUserFromAuth()
                    .orElseThrow(() -> new IllegalStateException("User not authenticated"))
                    .getId();
            Match match = matchService.createMatch(playerId);

            return ResponseEntity.ok(Map.of(
                    "inviteToken", match.getInviteToken(),
                    "matchId", match.getId(),
                    "playerId", playerId.toString()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    String.format("Failed to create match: %s", e.getMessage())
            );
        }
    }

    @PostMapping("/join")
    public ResponseEntity<?> joinMatch(
            @RequestParam String inviteToken) {
        try {

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

            return ResponseEntity.ok(Map.of(
                    "matchId", match.getId(),
                    "playerId", matchPlayerId.toString()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (GameRuleViolationException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    String.format("Failed to join match: %s", e.getMessage())
            );
        }
    }
}

