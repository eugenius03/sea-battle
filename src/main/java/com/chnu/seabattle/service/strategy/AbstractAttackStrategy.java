package com.chnu.seabattle.service.strategy;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.converter.MoveConverter;
import com.chnu.seabattle.dto.game.Cell;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.entity.Move;
import com.chnu.seabattle.entity.MoveResult;
import com.chnu.seabattle.entity.MoveType;
import com.chnu.seabattle.entity.Ship;
import com.chnu.seabattle.exception.ResourceNotFoundException;
import com.chnu.seabattle.service.MoveService;
import com.chnu.seabattle.util.BoardUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class AbstractAttackStrategy implements MoveStrategy {

    protected final MoveService moveService;
    protected final MoveConverter moveConverter;

    protected AbstractAttackStrategy(MoveService moveService, MoveConverter moveConverter) {
        this.moveService = moveService;
        this.moveConverter = moveConverter;
    }

    protected record HitResult(MoveResult result, Ship sunkShip) {
        static HitResult of(MoveResult result) {
            return new HitResult(result, null);
        }

        static HitResult ofSunk(MoveResult result, Ship ship) {
            return new HitResult(result, ship);
        }

        boolean isShipSunk() {
            return sunkShip != null;
        }
    }

    protected MatchPlayer getOpponent(Match match, UUID shooterId) {
        return match.getPlayers().stream()
                .filter(mp -> !mp.getId().equals(shooterId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorConstants.PLAYER_NOT_FOUND));
    }

    protected HitResult resolveHit(Match match, MatchPlayer opponent, Move move) {
        Optional<Ship> hitShipOpt = opponent.getShips().stream()
                .filter(ship -> BoardUtils.getOccupiedCells(ship)
                        .contains(new Cell(move.getTargetX(), move.getTargetY())))
                .findFirst();

        if (hitShipOpt.isEmpty()) {
            return HitResult.of(MoveResult.MISS);
        }

        Ship hitShip = hitShipOpt.get();
        hitShip.setHits(hitShip.getHits() + 1);

        if (hitShip.getHits() < hitShip.getShipType().getSize()) {
            return HitResult.of(MoveResult.HIT);
        }

        hitShip.setSunk(true);
        boolean allSunk = opponent.getShips().stream().allMatch(Ship::isSunk);
        if (allSunk) {
            match.setStatus(MatchStatus.FINISHED);
            match.setWinnerId(move.getShooterId());
            match.setFinishedAt(Instant.now());
            return HitResult.ofSunk(MoveResult.FINISHED, hitShip);
        }
        return HitResult.ofSunk(MoveResult.SUNK, hitShip);
    }

    protected Set<Cell> collectTargetedCells(Match match, UUID shooterId) {
        return match.getMoves().stream()
                .filter(m -> m.getShooterId().equals(shooterId))
                .map(m -> new Cell(m.getTargetX(), m.getTargetY()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    protected List<Move> createSurroundingMissMoves(Ship sunkShip, Match match, UUID shooterId, Set<Cell> alreadyTargeted) {
        Set<Cell> shipCells = BoardUtils.getOccupiedCells(sunkShip);

        Set<Cell> surroundingCells = new HashSet<>();
        for (Cell cell : shipCells) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int nx = cell.x() + dx;
                    int ny = cell.y() + dy;
                    if (BoardUtils.isWithinBounds(nx, ny)) {
                        surroundingCells.add(new Cell(nx, ny));
                    }
                }
            }
        }
        surroundingCells.removeAll(shipCells);
        surroundingCells.removeAll(alreadyTargeted);

        List<Move> missMoves = new ArrayList<>();
        for (Cell cell : surroundingCells) {
            Move missMove = Move.builder()
                    .match(match)
                    .shooterId(shooterId)
                    .targetX(cell.x())
                    .targetY(cell.y())
                    .moveType(MoveType.STANDARD)
                    .moveResult(MoveResult.MISS)
                    .isMoveOrigin(false)
                    .build();
            missMoves.add(moveService.create(missMove));
            alreadyTargeted.add(cell);
        }
        return missMoves;
    }
}