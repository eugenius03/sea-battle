package com.chnu.seabattle.service.strategy;

import com.chnu.seabattle.converter.MoveConverter;
import com.chnu.seabattle.dto.move.MoveRequest;
import com.chnu.seabattle.dto.move.MoveResponse;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.Move;
import com.chnu.seabattle.entity.MoveResult;
import com.chnu.seabattle.entity.MoveType;
import com.chnu.seabattle.service.MoveService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class StandardAttackStrategy extends AbstractAttackStrategy {

    public StandardAttackStrategy(MoveService moveService, MoveConverter moveConverter) {
        super(moveService, moveConverter);
    }

    @Override
    public MoveType getType() {
        return MoveType.STANDARD;
    }

    @Override
    public void validate(Match match, MoveRequest moveRequest) {
        // No implementation, because it happens before method is called
    }

    @Override
    @Transactional
    public List<MoveResponse> execute(Match match, MoveRequest moveRequest) {
        validate(match, moveRequest);

        MatchPlayer opponent = getOpponent(match, moveRequest.shooterId());

        Move move = Move.builder()
                .match(match)
                .shooterId(moveRequest.shooterId())
                .targetX(moveRequest.x())
                .targetY(moveRequest.y())
                .moveType(MoveType.STANDARD)
                .build();

        MoveResult result = resolveHit(match, opponent, move);
        move.setMoveResult(result);

        if (result == MoveResult.MISS) {
            match.setCurrentPlayerTurnId(opponent.getId());
        }

        move = moveService.create(move);
        return List.of(moveConverter.toResponse(move));
    }
}