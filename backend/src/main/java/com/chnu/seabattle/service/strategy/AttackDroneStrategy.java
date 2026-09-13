package com.chnu.seabattle.service.strategy;

import com.chnu.seabattle.config.DroneConfig;
import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.converter.MoveConverter;
import com.chnu.seabattle.dto.game.Cell;
import com.chnu.seabattle.dto.move.MoveRequest;
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
import java.util.Set;
import java.util.UUID;

@Component
public class AttackDroneStrategy extends AbstractAttackStrategy {

    private final Random random = new Random();
    private final DroneConfig droneConfig;

    public AttackDroneStrategy(MoveService moveService, MoveConverter moveConverter, DroneConfig droneConfig) {
        super(moveService, moveConverter);
        this.droneConfig = droneConfig;
    }

    @Override
    public MoveType getType() {
        return MoveType.ATTACK_DRONE;
    }

    @Override
    public void validate(Match match, UUID shooterId, MoveRequest moveRequest) {
        long used = moveService.countUsagesForMoveType(match.getId(), shooterId, getType());
        if (used >= droneConfig.getAttackMaxUsages()) {
            throw new GameRuleViolationException(ErrorConstants.DRONE_LIMIT_EXCEEDED);
        }
    }

    @Override
    @Transactional
    public List<Move> execute(Match match, UUID shooterId, MoveRequest moveRequest) {
        validate(match, shooterId, moveRequest);

        MatchPlayer opponent = getOpponent(match, shooterId);

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

        Set<Cell> targetedCells = collectTargetedCells(match, shooterId);
        List<Move> moves = new ArrayList<>();
        boolean gameOver = false;
        boolean isFirstMove = true;

        for (Cell cell : targetCells) {
            Move move = Move.builder()
                    .match(match)
                    .shooterId(shooterId)
                    .targetX(cell.x())
                    .targetY(cell.y())
                    .moveType(MoveType.ATTACK_DRONE)
                    .isMoveOrigin(isFirstMove)
                    .build();

            isFirstMove = false;

            HitResult hitResult = resolveHit(match, opponent, move);
            move.setMoveResult(hitResult.result());
            Move savedMove = moveService.create(move);
            moves.add(savedMove);
            targetedCells.add(cell);

            if (hitResult.isShipSunk()) {
                moves.addAll(createSurroundingMissMoves(hitResult.sunkShip(), match, shooterId, targetedCells));
            }

            if (MoveResult.FINISHED.equals(hitResult.result())) {
                gameOver = true;
                break;
            }
        }

        if (!gameOver) {
            match.setCurrentPlayerTurnId(opponent.getId());
        }

        return moves;
    }
}