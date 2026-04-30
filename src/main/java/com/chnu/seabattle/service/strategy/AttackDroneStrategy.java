package com.chnu.seabattle.service.strategy;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.converter.MoveConverter;
import com.chnu.seabattle.dto.Cell;
import com.chnu.seabattle.dto.move.MoveRequest;
import com.chnu.seabattle.dto.move.MoveResponse;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.Move;
import com.chnu.seabattle.entity.MoveResult;
import com.chnu.seabattle.entity.MoveType;
import com.chnu.seabattle.exception.GameRuleViolationException;
import com.chnu.seabattle.service.MoveService;
import com.chnu.seabattle.util.BoardUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Component
public class AttackDroneStrategy extends AbstractAttackStrategy {

    private final Random random = new Random();

    public AttackDroneStrategy(MoveService moveService, MoveConverter moveConverter) {
        super(moveService, moveConverter);
    }

    @Override
    public MoveType getType() {
        return MoveType.ATTACK_DRONE;
    }

    @Override
    public void validate(Match match, MoveRequest moveRequest) {
        long used = moveService.countByMatchIdAndShooterIdAndMoveTypeAndIsMoveOriginTrue(
                match.getId(), moveRequest.shooterId(), getType());
        if (used >= getType().getMaxUsesPerMatch()) {
            throw new GameRuleViolationException(ErrorConstants.DRONE_LIMIT_EXCEEDED);
        }
    }

    @Override
    @Transactional
    public List<MoveResponse> execute(Match match, MoveRequest moveRequest) {
        validate(match, moveRequest);

        MatchPlayer opponent = getOpponent(match, moveRequest.shooterId());


        int cx = moveRequest.x();
        int cy = moveRequest.y();
        List<Cell> validCells = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = cx + dx;
                int ny = cy + dy;
                if (!BoardUtils.isWithinBounds(nx, ny)) continue;
                validCells.add(new Cell(nx, ny));
            }
        }
        Collections.shuffle(validCells);
        int shotCount = Math.min(validCells.size(), 3 + random.nextInt(4));

        List<Cell> targetCells = validCells.stream()
                .limit(shotCount)
                .toList();

        List<Move> moves = new ArrayList<>();
        boolean gameOver = false;
        boolean isFirstMove = true;

        for (Cell cell : targetCells) {
            Move move = Move.builder()
                    .match(match)
                    .shooterId(moveRequest.shooterId())
                    .targetX(cell.x())
                    .targetY(cell.y())
                    .moveType(MoveType.ATTACK_DRONE)
                    .isMoveOrigin(isFirstMove)
                    .build();

            isFirstMove = false;

            MoveResult result = resolveHit(match, opponent, move);
            move.setMoveResult(result);
            moves.add(moveService.create(move));

            if (MoveResult.FINISHED.equals(result)) {
                gameOver = true;
                break;
            }
        }

        if (!gameOver) {
            match.setCurrentPlayerTurnId(opponent.getId());
        }

        return moves.stream().map(moveConverter::toResponse).toList();
    }
}