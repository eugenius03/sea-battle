package com.chnu.seabattle.controller;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.converter.MoveConverter;
import com.chnu.seabattle.converter.ShipConverter;
import com.chnu.seabattle.dto.game.GameInfoResponse;
import com.chnu.seabattle.dto.move.MoveRequest;
import com.chnu.seabattle.dto.move.MoveResponse;
import com.chnu.seabattle.dto.ship.ShipRequest;
import com.chnu.seabattle.dto.ship.ShipResponse;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.entity.Orientation;
import com.chnu.seabattle.entity.Ship;
import com.chnu.seabattle.exception.ResourceNotFoundException;
import com.chnu.seabattle.service.GameService;
import com.chnu.seabattle.service.MatchPlayerService;
import com.chnu.seabattle.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;
    private final MatchPlayerService matchPlayerService;
    private final UserService userService;
    private final MoveConverter moveConverter;
    private final ShipConverter shipConverter;

    private MatchPlayer resolveMatchPlayer(String inviteToken) {
        UUID userId = userService.getAuthenticatedUser().getId();
        return matchPlayerService.findByMatchInviteTokenAndUserId(inviteToken, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorConstants.PLAYER_NOT_FOUND));
    }

    @PostMapping("/{inviteToken}/place-ship")
    public Long placeShip(
            @PathVariable String inviteToken,
            @RequestBody @Valid ShipRequest shipRequest
    ) {
        MatchPlayer player = resolveMatchPlayer(inviteToken);
        Ship ship = gameService.placeShip(player, shipRequest);
        return ship.getId();
    }

    @PostMapping("/{inviteToken}/generate-random-ships")
    public List<ShipResponse> generateRandomShips(@PathVariable String inviteToken) {
        MatchPlayer player = resolveMatchPlayer(inviteToken);
        return gameService.generateRandomShips(player)
                .stream().map(shipConverter::toResponse).toList();
    }

    @PostMapping("/{inviteToken}/move-ship/{shipId}")
    public Long moveShip(
            @PathVariable String inviteToken,
            @PathVariable Long shipId,
            @RequestParam int startX,
            @RequestParam int startY,
            @RequestParam Orientation orientation
    ) {
        MatchPlayer player = resolveMatchPlayer(inviteToken);
        gameService.moveShip(player, shipId, startX, startY, orientation);
        return shipId;
    }

    @PostMapping("/{inviteToken}/mark-ready")
    public void markReady(@PathVariable String inviteToken) {
        MatchPlayer player = resolveMatchPlayer(inviteToken);
        gameService.markReady(player);
    }

    @PostMapping("/{inviteToken}/fire")
    public List<MoveResponse> fire(
            @PathVariable String inviteToken,
            @Valid @RequestBody MoveRequest moveRequest
    ) {
        MatchPlayer shooter = resolveMatchPlayer(inviteToken);
        return gameService.executeMove(shooter, moveRequest).stream()
                .map(moveConverter::toResponse)
                .toList();
    }

    @GetMapping("{inviteToken}/info")
    public GameInfoResponse info(@PathVariable String inviteToken) {
        MatchPlayer matchPlayer = resolveMatchPlayer(inviteToken);
        Match match = matchPlayer.getMatch();

        if (MatchStatus.IN_PROGRESS.equals(match.getStatus())) {
            gameService.handleReconnect(match, matchPlayer);
        }

        return gameService.getMatchInfo(match, matchPlayer);
    }
}