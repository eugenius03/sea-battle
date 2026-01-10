package com.chnu.seabattle.controller;

import com.chnu.seabattle.entity.*;
import com.chnu.seabattle.exception.GameRuleViolationException;
import com.chnu.seabattle.service.GameService;
import com.chnu.seabattle.service.WebSocketService;
import com.chnu.seabattle.service.serviceImpl.MatchServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
public class GameApiController {

    private final GameService gameService;
    private final WebSocketService webSocketService;
    private final MatchServiceImpl matchServiceImpl;


    @PostMapping("/{matchId}/place-ship")
    public ResponseEntity<?> placeShip(
            @PathVariable Long matchId,
            @RequestParam UUID playerId,
            @RequestParam ShipType type,
            @RequestParam int startX,
            @RequestParam int startY,
            @RequestParam Orientation orientation
    ) {
        try {

            gameService.placeShip(matchId, playerId, type, startX, startY, orientation);
            return ResponseEntity.ok().build();

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (GameRuleViolationException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{matchId}/mark-ready")
    public ResponseEntity<?> markReady(
            @PathVariable Long matchId,
            @RequestParam UUID playerId
    ) {
        try {
            Match match = gameService.markReady(matchId, playerId);
            UUID opponentId = gameService.getOpponentPlayerId(match, playerId);

            if (match.getPlayers().stream().allMatch(MatchPlayer::isReady)) {
                webSocketService.updateMatchStatus(matchId, MatchStatus.IN_PROGRESS, match.getCurrentPlayerTurnId());
            }

            webSocketService.sendPlayerReadyMessage(matchId, opponentId);

            return ResponseEntity.ok().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{matchId}/fire")
    public ResponseEntity<?> fire(
            @PathVariable Long matchId,
            @RequestParam UUID shooterId,
            @RequestParam int x,
            @RequestParam int y
    ) {
        try {
            MoveResult result = gameService.fire(matchId, shooterId, x, y);
            Match match = matchServiceImpl.getMatchById(matchId);

            webSocketService.sendOpponentMoveMessage(
                    matchId,
                    gameService.getOpponentPlayerId(match, shooterId),
                    x,
                    y,
                    result,
                    match.getCurrentPlayerTurnId()
            );

            if (result == MoveResult.FINISHED) {
                webSocketService.updateMatchStatus(matchId, MatchStatus.FINISHED, null);
            }

            return ResponseEntity.ok(result);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (GameRuleViolationException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}

