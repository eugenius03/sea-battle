package com.chnu.seabattle.dto.move;

import com.chnu.seabattle.entity.MoveResult;

public record MoveResponse(int targetX, int targetY, MoveResult moveResult) {
}