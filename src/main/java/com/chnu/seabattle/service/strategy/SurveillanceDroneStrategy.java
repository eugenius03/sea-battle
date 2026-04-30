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
import com.chnu.seabattle.exception.ResourceNotFoundException;
import com.chnu.seabattle.service.MoveService;
import com.chnu.seabattle.util.BoardUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SurveillanceDroneStrategy implements MoveStrategy {

    private final MoveService moveService;
    private final MoveConverter moveConverter;

    @Override
    public MoveType getType() {
        return MoveType.SURVEILLANCE_DRONE;
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

        MatchPlayer opponent = match.getPlayers().stream()
                .filter(mp -> !mp.getId().equals(moveRequest.shooterId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorConstants.PLAYER_NOT_FOUND));

        Set<Cell> shipCells = opponent.getShips().stream()
                .flatMap(ship -> BoardUtils.getOccupiedCells(ship).stream())
                .collect(Collectors.toSet());

        int cx = moveRequest.x();
        int cy = moveRequest.y();
        List<Move> found = new ArrayList<>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = cx + dx;
                int ny = cy + dy;
                if (!BoardUtils.isWithinBounds(nx, ny)) continue;
                boolean isCenter = nx == cx && ny == cy;
                MoveResult result = shipCells.contains(new Cell(nx, ny))
                        ? MoveResult.SURVEILLANCE
                        : MoveResult.MISS;
                found.add(Move.builder()
                        .match(match)
                        .shooterId(moveRequest.shooterId())
                        .targetX(nx)
                        .targetY(ny)
                        .moveType(MoveType.SURVEILLANCE_DRONE)
                        .moveResult(result)
                        .isMoveOrigin(isCenter)
                        .createdAt(Instant.now())
                        .build()
                );
            }
        }
        match.setCurrentPlayerTurnId(
                opponent.getId()
        );

        found.forEach(moveService::create);

        return found.stream().map(moveConverter::toResponse).toList();
    }
}