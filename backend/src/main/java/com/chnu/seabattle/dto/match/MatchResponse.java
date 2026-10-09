package com.chnu.seabattle.dto.match;

import com.chnu.seabattle.dto.move.ReplayMoveResponse;
import com.chnu.seabattle.entity.MatchStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record MatchResponse(
        String inviteToken,
        MatchStatus status,
        List<MatchParticipantResponse> players,
        List<ReplayMoveResponse> moves,
        Instant createdAt,
        Instant finishedAt
) {
}