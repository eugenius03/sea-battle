package com.chnu.seabattle.dto.match;

import com.chnu.seabattle.enums.MatchResult;
import lombok.Builder;

import java.time.Instant;

@Builder
public record MatchHistoryResponse(
        String id,
        MatchResult result,
        Instant playedAt,
        Long durationSeconds
) {
}
