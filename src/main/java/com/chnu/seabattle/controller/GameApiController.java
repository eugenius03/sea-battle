package com.chnu.seabattle.controller;

import com.chnu.seabattle.dto.FireResult;
import com.chnu.seabattle.dto.GameInfoResponse;
import com.chnu.seabattle.dto.ship.ShipResponse;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.entity.MoveResult;
import com.chnu.seabattle.entity.Orientation;
import com.chnu.seabattle.entity.Ship;
import com.chnu.seabattle.entity.ShipType;
import com.chnu.seabattle.entity.User;
import com.chnu.seabattle.exception.ResourceNotFoundException;
import com.chnu.seabattle.service.GameService;
import com.chnu.seabattle.service.MatchPlayerService;
import com.chnu.seabattle.service.MatchService;
import com.chnu.seabattle.service.UserService;
import com.chnu.seabattle.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
public class GameApiController {

    private final GameService gameService;
    private final WebSocketService webSocketService;
    private final MatchService matchService;
    private final MatchPlayerService matchPlayerService;
    private final UserService userService;

    @PostMapping("/{matchId}/place-ship")
    public Long placeShip(
            @PathVariable Long matchId,
            @RequestParam UUID playerId,
            @RequestParam ShipType type,
            @RequestParam int startX,
            @RequestParam int startY,
            @RequestParam Orientation orientation
    ) {
        Ship ship = gameService.placeShip(matchId, playerId, type, startX, startY, orientation);
        return ship.getId();
    }

    @PostMapping("/{matchId}/generate-random-ships")
    public List<ShipResponse> generateRandomShips(
            @PathVariable Long matchId,
            @RequestParam UUID playerId
    ) {
        return gameService.generateRandomShips(matchId, playerId);
    }

    @PostMapping("/{matchId}/move-ship/{shipId}")
    public Long moveShip(
            @PathVariable Long matchId,
            @PathVariable Long shipId,
            @RequestParam UUID playerId,
            @RequestParam int startX,
            @RequestParam int startY,
            @RequestParam Orientation orientation
    ) {
        gameService.moveShip(matchId, playerId, shipId, startX, startY, orientation);
        return shipId;
    }

    @PostMapping("/{matchId}/mark-ready")
    public void markReady(
            @PathVariable Long matchId,
            @RequestParam UUID playerId
    ) {
        Match match = gameService.markReady(matchId, playerId);
        UUID opponentId = gameService.getOpponentPlayerId(match, playerId);

        if (match.getPlayers().stream().allMatch(MatchPlayer::isReady)) {
            webSocketService.updateMatchStatus(matchId, MatchStatus.IN_PROGRESS, match.getCurrentPlayerTurnId());
        }

        webSocketService.sendPlayerReadyMessage(matchId, opponentId);
    }

    @PostMapping("/{matchId}/fire")
    public MoveResult fire(
            @PathVariable Long matchId,
            @RequestParam UUID shooterId,
            @RequestParam int x,
            @RequestParam int y
    ) {
        FireResult result = gameService.fire(matchId, shooterId, x, y);

        UUID opponentId = gameService.getOpponentPlayerId(result.match(), shooterId);
        boolean isItOpponentsTurn = opponentId.equals(result.match().getCurrentPlayerTurnId());

        webSocketService.sendOpponentMoveMessage(
                matchId,
                opponentId,
                x,
                y,
                result.moveResult(),
                isItOpponentsTurn
        );

        if (result.moveResult() == MoveResult.FINISHED) {
            webSocketService.updateMatchStatus(matchId, MatchStatus.FINISHED, null);
        }

        return result.moveResult();
    }

    @GetMapping("{inviteToken}/info")
    public GameInfoResponse info(@PathVariable String inviteToken) {
        User user = userService.getAuthenticatedUser();

        Match match = matchService.getMatchByInviteToken(inviteToken);

        MatchPlayer matchPlayer = matchPlayerService.findByMatchIdAndUserId(match.getId(), user.getId()).orElseThrow(
                () -> new ResourceNotFoundException(String.format("MatchPlayer not found for match %d", match.getId()))
        );

        if (MatchStatus.IN_PROGRESS.equals(match.getStatus())) {
            gameService.handleReconnect(match, matchPlayer);
        }

        return gameService.getMatchInfo(match.getId(), matchPlayer);
    }
}
