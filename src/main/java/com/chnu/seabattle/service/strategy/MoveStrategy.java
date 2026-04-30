package com.chnu.seabattle.service.strategy;

import com.chnu.seabattle.dto.move.MoveRequest;
import com.chnu.seabattle.dto.move.MoveResponse;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MoveType;

import java.util.List;

public interface MoveStrategy {
    MoveType getType();

    void validate(Match match, MoveRequest moveRequest);

    /**
     * Executes the move and returns the resulting moves to persist.
     * Most strategies return one Move; some (like attack drone hitting
     * multiple ships) might want to return several, OR return one Move
     * with the strongest result. Pick whichever feels right.
     */
    List<MoveResponse> execute(Match match, MoveRequest moveRequest);

}
