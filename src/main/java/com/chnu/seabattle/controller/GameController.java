package com.chnu.seabattle.controller;

import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.exception.GameRuleViolationException;
import com.chnu.seabattle.service.AuthService;
import com.chnu.seabattle.service.GameService;
import com.chnu.seabattle.service.MatchService;
import com.chnu.seabattle.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/game")
@RequiredArgsConstructor
public class GameController {

    private final MatchService matchService;
    private final WebSocketService webSocketService;
    private final GameService gameService;
    private final AuthService authService;

    @GetMapping
    public String index() {
        return "index";
    }

    @PostMapping
    public String createMatch(RedirectAttributes redirectAttributes, Model model) {
        UUID userId = authService.getAuthenticatedUser().getId();
        Match match = matchService.createMatch(userId);
        MatchPlayer player = match.getPlayers().getFirst();
        model.addAttribute("matchPlayerId", player.getId());

        return String.format("redirect:/game/%s", match.getInviteToken());
    }

    @GetMapping("/{inviteToken}")
    public String gamePage(@PathVariable String inviteToken) {
        UUID userId = authService.getAuthenticatedUser().getId();
        Match match = matchService.getMatchByInviteToken(inviteToken);
        match.getPlayers()
                .stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new GameRuleViolationException("You are not a player in this match."));

        return "game";
    }

    @GetMapping("/join/{inviteToken}")
    public String joinPage(@PathVariable String inviteToken) {
        UUID userId = authService.getAuthenticatedUser().getId();

        try {
            Match match = matchService.joinMatch(userId, inviteToken);
            UUID opponentId = gameService.getOpponentPlayerId(match, userId);
            webSocketService.handleOpponentConnected(match.getId(),
                    opponentId);
        } catch (GameRuleViolationException e) {
            return String.format("redirect:/game?error=%s", e.getMessage());
        }

        return String.format("redirect:/game/%s", inviteToken);
    }
}

