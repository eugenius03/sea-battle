package com.chnu.seabattle.dto.move;

import com.chnu.seabattle.entity.MoveResult;

import java.io.Serializable;

/**
 * DTO for {@link com.chnu.seabattle.entity.Move}
 */
public record MoveResponse(
        int targetX, int targetY, MoveResult moveResult
) implements Serializable {
}