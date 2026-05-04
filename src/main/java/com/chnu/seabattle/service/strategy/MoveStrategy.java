package com.chnu.seabattle.service.strategy;

import com.chnu.seabattle.dto.move.MoveRequest;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.Move;
import com.chnu.seabattle.entity.MoveType;

import java.util.List;
import java.util.UUID;

public interface MoveStrategy {
    MoveType getType();

    void validate(Match match, UUID shooterId, MoveRequest moveRequest);

    List<Move> execute(Match match, UUID shooterId, MoveRequest moveRequest);

}
