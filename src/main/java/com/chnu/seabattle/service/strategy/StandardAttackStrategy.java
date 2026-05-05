package com.chnu.seabattle.service.strategy;

import com.chnu.seabattle.converter.MoveConverter;
import com.chnu.seabattle.dto.game.Cell;
import com.chnu.seabattle.dto.move.MoveRequest;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.Move;
import com.chnu.seabattle.entity.MoveResult;
import com.chnu.seabattle.entity.MoveType;
import com.chnu.seabattle.service.MoveService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    public void validate(Match match, UUID shooterId, MoveRequest moveRequest) {
        // No additional validation is required, validated in service
    }

    @Override
    @Transactional
    public List<Move> execute(Match match, UUID shooterId, MoveRequest moveRequest) {
        validate(match, shooterId, moveRequest);

        MatchPlayer opponent = getOpponent(match, shooterId);

        Move move = Move.builder()
                .match(match)
                .shooterId(shooterId)
                .targetX(moveRequest.x())
                .targetY(moveRequest.y())
                .moveType(MoveType.STANDARD)
                .build();

        Set<Cell> targetedCells = collectTargetedCells(match, shooterId);
        HitResult hitResult = resolveHit(match, opponent, move);
        move.setMoveResult(hitResult.result());

        if (hitResult.result() == MoveResult.MISS) {
            match.setCurrentPlayerTurnId(opponent.getId());
        }

        move = moveService.create(move);
        targetedCells.add(new Cell(move.getTargetX(), move.getTargetY()));

        if (!hitResult.isShipSunk()) {
            return List.of(move);
        }

        List<Move> result = new ArrayList<>();
        result.add(move);
        result.addAll(createSurroundingMissMoves(hitResult.sunkShip(), match, shooterId, targetedCells));

        return result;
    }
}