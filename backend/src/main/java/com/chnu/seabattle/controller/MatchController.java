package com.chnu.seabattle.controller;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.dto.match.MatchHistoryResponse;
import com.chnu.seabattle.dto.match.MatchInviteResponse;
import com.chnu.seabattle.dto.match.MatchResponse;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.exception.ResourceNotFoundException;
import com.chnu.seabattle.service.GameBotService;
import com.chnu.seabattle.service.MatchService;
import com.chnu.seabattle.service.MatchmakingService;
import com.chnu.seabattle.service.RematchFacade;
import com.chnu.seabattle.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;
    private final UserService userService;
    private final MatchmakingService matchmakingService;
    private final GameBotService gameBotService;
    private final RematchFacade rematchFacade;

    @PostMapping("/create")
    public MatchInviteResponse createMatch() {
        UUID userId = userService.getAuthenticatedUser().getId();
        Match match = matchService.createMatch(userId);

        return new MatchInviteResponse(match.getInviteToken(), userId);
    }

    @PostMapping("/bot")
    public MatchInviteResponse createBotMatch() {
        UUID userId = userService.getAuthenticatedUser().getId();
        Match match = gameBotService.createBotMatch(userId);

        return new MatchInviteResponse(match.getInviteToken(), userId);
    }

    @PostMapping("/join")
    public MatchInviteResponse joinMatch(@RequestParam String inviteToken) {
        UUID playerId = userService.getAuthenticatedUser().getId();
        Match match = matchService.joinMatch(playerId, inviteToken);

        return new MatchInviteResponse(match.getInviteToken(), playerId);
    }

    @PostMapping("/{inviteToken}/rematch")
    public ResponseEntity<MatchInviteResponse> rematch(@PathVariable String inviteToken) {
        UUID userId = userService.getAuthenticatedUser().getId();
        Optional<MatchInviteResponse> rematchResponse = rematchFacade.rematch(inviteToken, userId);

        return rematchResponse
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.accepted().build());
    }

    @PostMapping("/find")
    public ResponseEntity<Void> findGame() {
        UUID userId = userService.getAuthenticatedUser().getId();
        matchmakingService.joinQueue(userId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/history/{username}")
    public List<MatchHistoryResponse> getMatchHistory(@PathVariable String username, Pageable pageable) {
        UUID userId = userService.findByUsername(username).orElseThrow(
                () -> new ResourceNotFoundException(ErrorConstants.USER_NOT_FOUND)
        ).getId();
        return matchService.getMatchHistory(userId, pageable);
    }

    @GetMapping("{inviteToken}")
    public MatchResponse getMatch(@PathVariable String inviteToken) {
        return matchService.getMatch(inviteToken);
    }
}