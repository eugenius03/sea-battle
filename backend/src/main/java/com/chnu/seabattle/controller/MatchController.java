package com.chnu.seabattle.controller;

import com.chnu.seabattle.dto.match.MatchResponse;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.service.MatchService;
import com.chnu.seabattle.service.MatchmakingService;
import com.chnu.seabattle.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;


@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;
    private final UserService userService;
    private final MatchmakingService matchmakingService;

    @PostMapping("/create")
    public MatchResponse createMatch() {
        UUID userId = userService.getAuthenticatedUser().getId();
        Match match = matchService.createMatch(userId);

        return new MatchResponse(match.getInviteToken(), userId);
    }

    @PostMapping("/join")
    public MatchResponse joinMatch(@RequestParam String inviteToken) {
        UUID playerId = userService.getAuthenticatedUser().getId();
        Match match = matchService.joinMatch(playerId, inviteToken);

        return new MatchResponse(match.getInviteToken(), playerId);
    }

    @PostMapping("/{inviteToken}/rematch")
    public ResponseEntity<MatchResponse> rematch(@PathVariable String inviteToken) {
        UUID userId = userService.getAuthenticatedUser().getId();
        Optional<MatchResponse> rematchResponse = matchService.processRematch(inviteToken, userId);

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
}