package com.chnu.seabattle.dto;

import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MoveResult;

public record FireResult(
        MoveResult moveResult,
        Match match
) {


}
