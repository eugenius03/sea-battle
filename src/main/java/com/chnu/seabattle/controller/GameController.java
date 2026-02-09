package com.chnu.seabattle.controller;

import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.User;
import com.chnu.seabattle.repository.UserRepository;
import com.chnu.seabattle.service.GameService;
import com.chnu.seabattle.service.MatchService;
import com.chnu.seabattle.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/game")
@RequiredArgsConstructor
public class GameController {

    private final MatchService matchService;
    private final UserRepository userRepository;
    private final WebSocketService webSocketService;
    private final GameService gameService;

    @GetMapping
    public String index() {
        return "index";
    }

    @PostMapping
    public String createMatch(RedirectAttributes redirectAttributes, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            redirectAttributes.addFlashAttribute("error", "You must be logged in to create a match.");
            return "redirect:/login";
        }
        String username = authentication.getName();
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "User not found.");
            return "redirect:/login";
        }
        UUID userId = userOpt.get().getId();
        Match match = matchService.createMatch(userId);
        MatchPlayer player = match.getPlayers().getFirst();
        model.addAttribute("matchPlayerId", player.getId());

        return String.format("redirect:/game/%s", match.getInviteToken());
    }

    @GetMapping("/{inviteToken}")
    public String gamePage(@PathVariable String inviteToken, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            model.addAttribute("error", "You must be logged in to join a match.");
            return "redirect:/login";
        }
        String username = authentication.getName();
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            model.addAttribute("error", "User not found.");
            return "redirect:/login";
        }

        Match match = matchService.getMatchByInviteToken(inviteToken);
        MatchPlayer player = match.getPlayers()
                .stream()
                .filter(p -> p.getUserId().equals(userOpt.get().getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("You are not a player in this match."));

        model.addAttribute("matchId", match.getId());
        model.addAttribute("reconnectToken", player.getReconnectToken());
        model.addAttribute("matchPlayerId", player.getId());
        return "game";
    }

    @GetMapping("/join/{inviteToken}")
    public String joinPage(@PathVariable String inviteToken, Model model) {
        model.addAttribute("inviteToken", inviteToken);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            model.addAttribute("error", "You must be logged in to join a match.");
            return "redirect:/login";
        }
        String username = authentication.getName();
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            model.addAttribute("error", "User not found.");
            return "redirect:/login";
        }
        UUID playerId = userOpt.get().getId();

        try {
            Match match = matchService.joinMatch(playerId, inviteToken);
            UUID opponentId = gameService.getOpponentPlayerId(match, playerId);
            webSocketService.handleOpponentConnected(match.getId(),
                    opponentId);
        } catch (Exception e) {
            return String.format("redirect:/game?error=%s", e.getMessage());
        }

        return String.format("redirect:/game/%s", inviteToken);
    }
}

