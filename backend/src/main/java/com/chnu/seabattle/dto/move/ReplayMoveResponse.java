package com.chnu.seabattle.dto.move;

import com.chnu.seabattle.entity.MoveResult;
import com.chnu.seabattle.entity.MoveType;
import lombok.Builder;

import java.util.UUID;

@Builder
public record ReplayMoveResponse(
        UUID shooterId,
        int targetX,
        int targetY,
        MoveType moveType,
        MoveResult moveResult,
        boolean isMoveOrigin
) {
}
