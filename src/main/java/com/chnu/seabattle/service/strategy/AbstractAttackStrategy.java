package com.chnu.seabattle.service.strategy;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.converter.MoveConverter;
import com.chnu.seabattle.dto.Cell;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.entity.Move;
import com.chnu.seabattle.entity.MoveResult;
import com.chnu.seabattle.entity.Ship;
import com.chnu.seabattle.exception.ResourceNotFoundException;
import com.chnu.seabattle.service.MoveService;
import com.chnu.seabattle.util.BoardUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public abstract class AbstractAttackStrategy implements MoveStrategy {

    protected final MoveService moveService;
    protected final MoveConverter moveConverter;

    protected AbstractAttackStrategy(MoveService moveService, MoveConverter moveConverter) {
        this.moveService = moveService;
        this.moveConverter = moveConverter;
    }

    protected MatchPlayer getOpponent(Match match, UUID shooterId) {
        return match.getPlayers().stream()
                .filter(mp -> !mp.getId().equals(shooterId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorConstants.PLAYER_NOT_FOUND));
    }

    /**
     * Applies a hit to the cell at (move.targetX, move.targetY).
     * Mutates ship state and match state if game ends.
     * Turn management is the caller's responsibility.
     */
    protected MoveResult resolveHit(Match match, MatchPlayer opponent, Move move) {
        Optional<Ship> hitShipOpt = opponent.getShips().stream()
                .filter(ship -> BoardUtils.getOccupiedCells(ship)
                        .contains(new Cell(move.getTargetX(), move.getTargetY())))
                .findFirst();

        if (hitShipOpt.isEmpty()) {
            return MoveResult.MISS;
        }

        Ship hitShip = hitShipOpt.get();
        hitShip.setHits(hitShip.getHits() + 1);

        if (hitShip.getHits() < hitShip.getShipType().getSize()) {
            return MoveResult.HIT;
        }

        hitShip.setSunk(true);
        boolean allSunk = opponent.getShips().stream().allMatch(Ship::isSunk);
        if (allSunk) {
            match.setStatus(MatchStatus.FINISHED);
            match.setWinnerId(move.getShooterId());
            match.setFinishedAt(Instant.now());
            return MoveResult.FINISHED;
        }
        return MoveResult.SUNK;
    }
}