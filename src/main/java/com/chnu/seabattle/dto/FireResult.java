package com.chnu.seabattle.dto;

import com.chnu.seabattle.dto.move.MoveResponse;
import com.chnu.seabattle.entity.Match;

import java.util.List;

public record FireResult(
        List<MoveResponse> moveResponses,
        Match match
) {


}
