package com.chnu.seabattle.service.impl;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.converter.MoveConverter;
import com.chnu.seabattle.converter.ShipConverter;
import com.chnu.seabattle.dto.FireResult;
import com.chnu.seabattle.dto.GameInfoResponse;
import com.chnu.seabattle.dto.move.MoveRequest;
import com.chnu.seabattle.dto.move.MoveResponse;
import com.chnu.seabattle.dto.ship.ShipResponse;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.entity.Move;
import com.chnu.seabattle.entity.MoveType;
import com.chnu.seabattle.entity.Orientation;
import com.chnu.seabattle.entity.Ship;
import com.chnu.seabattle.entity.ShipType;
import com.chnu.seabattle.exception.GameRuleViolationException;
import com.chnu.seabattle.exception.ResourceNotFoundException;
import com.chnu.seabattle.repository.ShipRepository;
import com.chnu.seabattle.service.GameService;
import com.chnu.seabattle.service.MatchPlayerService;
import com.chnu.seabattle.service.MatchService;
import com.chnu.seabattle.service.MoveService;
import com.chnu.seabattle.service.WebSocketService;
import com.chnu.seabattle.service.strategy.MoveStrategy;
import com.chnu.seabattle.util.BoardUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.counting;

@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {

    private static final int GRID_SIZE = 10;
    private static final int MAX_PLACEMENT_ATTEMPTS = 100;
    private final Random random = new Random();

    private final MatchService matchService;
    private final ShipRepository shipRepository;
    private final MatchPlayerService matchPlayerService;
    private final MoveConverter moveConverter;
    private final ShipConverter shipConverter;
    private final WebSocketService webSocketService;

    private final Map<MoveType, MoveStrategy> strategies;
    private final MoveService moveService;

    private void requireStatuses(Match match, MatchStatus... expected) {
        boolean isValid = Arrays.stream(expected)
                .anyMatch(status -> status.equals(match.getStatus()));

        if (!isValid) {
            throw new GameRuleViolationException(
                    String.format(ErrorConstants.INVALID_MATCH_STATE, match.getStatus())
            );
        }
    }


    private MatchPlayer getMatchPlayer(Match match, UUID playerId) {
        return match.getPlayers()
                .stream()
                .filter(mp -> mp.getId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorConstants.PLAYER_NOT_FOUND));
    }

    private boolean isPlayerTurn(Match match, UUID playerId) {
        return match.getCurrentPlayerTurnId().equals(playerId);
    }


    @Override
    @Transactional
    public Ship placeShip(Long matchId, UUID playerId, ShipType type, int startX, int startY, Orientation orientation) {
        MatchPlayer player = matchPlayerService.findById(playerId).orElseThrow(
                () -> new ResourceNotFoundException(ErrorConstants.PLAYER_NOT_FOUND)
        );

        Match match = matchService.findByIdForGame(matchId).orElseThrow(
                () -> new ResourceNotFoundException(ErrorConstants.MATCH_NOT_FOUND)
        );

        requireStatuses(match, MatchStatus.WAITING, MatchStatus.PLANNING);

        if (player.isReady()) {
            throw new GameRuleViolationException(ErrorConstants.PLAYER_ALREADY_READY);
        }

        List<Ship> updatedShips = player.getShips();

        if (!BoardUtils.isValidPlacement(updatedShips, type, startX, startY, orientation)) {
            throw new GameRuleViolationException(ErrorConstants.INVALID_SHIP_PLACEMENT);
        }

        Map<ShipType, Long> shipTypeCount = player.getShips().stream()
                .collect(
                        Collectors.groupingBy(Ship::getShipType, counting())
                );

        if (shipTypeCount.getOrDefault(type, 0L) >= type.allowedCount()) {
            throw new GameRuleViolationException(
                    String.format(ErrorConstants.SHIP_TYPE_LIMIT, type)
            );
        }

        Ship ship = Ship.builder()
                .matchPlayer(player)
                .shipType(type)
                .startX(startX)
                .startY(startY)
                .orientation(orientation)
                .hits(0)
                .isSunk(false)
                .build();
        ship = shipRepository.save(ship);

        updatedShips.add(ship);

        return ship;
    }

    @Override
    public List<ShipResponse> generateRandomShips(Long matchId, UUID playerId) {
        MatchPlayer player = matchPlayerService.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorConstants.PLAYER_NOT_FOUND));

        Match match = matchService.findByIdForGame(matchId).orElseThrow(
                () -> new ResourceNotFoundException(ErrorConstants.MATCH_NOT_FOUND)
        );

        requireStatuses(match, MatchStatus.WAITING, MatchStatus.PLANNING);

        if (player.isReady()) {
            throw new GameRuleViolationException(ErrorConstants.PLAYER_ALREADY_READY);
        }

        player.getShips().clear();

        List<ShipType> shipTypes = new ArrayList<>(Arrays.asList(ShipType.values()));
        Collections.shuffle(shipTypes, random);

        List<Ship> newShips = new ArrayList<>();

        for (ShipType type : shipTypes) {
            for (int i = 0; i < type.allowedCount(); i++) {
                for (int attempt = 0; attempt < MAX_PLACEMENT_ATTEMPTS; attempt++) {
                    int x = random.nextInt(GRID_SIZE);
                    int y = random.nextInt(GRID_SIZE);
                    Orientation orientation = random.nextBoolean()
                            ? Orientation.HORIZONTAL : Orientation.VERTICAL;

                    if (!BoardUtils.isValidPlacement(player.getShips(), type, x, y, orientation)) continue;

                    Ship ship = Ship.builder()
                            .matchPlayer(player)
                            .shipType(type)
                            .startX(x)
                            .startY(y)
                            .orientation(orientation)
                            .hits(0)
                            .isSunk(false)
                            .build();
                    newShips.add(ship);
                    player.getShips().add(ship);
                    break;
                }
            }
        }

        shipRepository.saveAll(newShips);
        return newShips.stream().map(shipConverter::toResponse).toList();
    }

    @Transactional
    @Override
    public Ship moveShip(Long matchId, UUID playerId, Long shipId, int x, int y, Orientation orientation) {
        Match match = matchService.findByIdForGame(matchId).orElseThrow(
                () -> new ResourceNotFoundException(ErrorConstants.MATCH_NOT_FOUND)
        );

        requireStatuses(match, MatchStatus.WAITING, MatchStatus.PLANNING);

        MatchPlayer player = getMatchPlayer(match, playerId);

        if (player.isReady()) {
            throw new GameRuleViolationException(ErrorConstants.PLAYER_ALREADY_READY);
        }

        List<Ship> ships = player.getShips();
        Ship ship = ships.stream().filter(s -> s.getId().equals(shipId)).findFirst().orElseThrow(
                () -> new ResourceNotFoundException(ErrorConstants.SHIP_NOT_FOUND)
        );

        ships.remove(ship);
        if (!BoardUtils.isValidPlacement(ships, ship.getShipType(), x, y, orientation)) {
            throw new GameRuleViolationException(ErrorConstants.INVALID_SHIP_PLACEMENT);
        }
        ship.setStartX(x);
        ship.setStartY(y);
        ship.setOrientation(orientation);

        ships.add(ship);
        return ship;

    }

    @Override
    @Transactional
    public Match markReady(Long matchId, UUID playerId) {
        Match match = matchService.findById(matchId).orElseThrow(
                () -> new ResourceNotFoundException(ErrorConstants.MATCH_NOT_FOUND)
        );
        requireStatuses(match, MatchStatus.PLANNING);
        MatchPlayer player = getMatchPlayer(match, playerId);
        player.setReady(true);
        boolean allReady = match.getPlayers().stream().allMatch(MatchPlayer::isReady);
        if (allReady) {
            match.setStatus(MatchStatus.IN_PROGRESS);

            MatchPlayer randomPlayer = match.getPlayers().get(random.nextInt(match.getPlayers().size()));
            match.setCurrentPlayerTurnId(randomPlayer.getId());
        }

        return match;
    }

    @Transactional
    @Override
    public FireResult executeMove(Long matchId, MoveRequest moveRequest) {
        Match match = matchService.findByIdForGame(matchId).orElseThrow(
                () -> new ResourceNotFoundException(ErrorConstants.MATCH_NOT_FOUND)
        );
        requireStatuses(match, MatchStatus.IN_PROGRESS);
        if (!isPlayerTurn(match, moveRequest.shooterId())) {
            throw new GameRuleViolationException(ErrorConstants.NOT_PLAYERS_TURN);
        }

        if (!BoardUtils.isWithinBounds(moveRequest.x(), moveRequest.y())) {
            throw new GameRuleViolationException(ErrorConstants.COORDINATES_OUT_OF_BOUNDS);
        }

        if (moveService.existsByMatchIdAndShooterIdAndTargetXAndTargetYAndMoveType(match.getId(), moveRequest.shooterId(),
                moveRequest.x(), moveRequest.y(), moveRequest.moveType())
        ) {
            throw new GameRuleViolationException(ErrorConstants.DUPLICATE_FIRE);
        }


        MoveStrategy strategy = strategies.get(moveRequest.moveType());
        if (strategy == null) throw new GameRuleViolationException(
                String.format(ErrorConstants.UNKNOWN_MOVE_TYPE, moveRequest.moveType()));

        List<MoveResponse> result = strategy.execute(match, moveRequest);

        return new FireResult(result, match);

    }

    @Override
    public void handleDisconnect(Match match, MatchPlayer player) {
        player.setConnected(false);
        player.setLastSeenAt(Instant.now());
    }

    @Override
    public void handleReconnect(Match match, MatchPlayer player) {
        player.setConnected(true);
        player.setLastSeenAt(Instant.now());

        webSocketService.handleReconnect(match.getId(), player.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public UUID getOpponentPlayerId(Match match, UUID playerId) {
        List<MatchPlayer> players = match.getPlayers();
        if (players.size() != 2) {
            throw new GameRuleViolationException(ErrorConstants.ONLY_TWO_PLAYERS);
        }

        return players.stream()
                .filter(p -> !p.getId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorConstants.PLAYER_NOT_FOUND))
                .getId();
    }

    @Override
    @Transactional(readOnly = true)
    public GameInfoResponse getMatchInfo(Long matchId, MatchPlayer matchPlayer) {
        Match match = matchService.findByIdForGame(matchId).orElseThrow(
                () -> new ResourceNotFoundException(ErrorConstants.MATCH_NOT_FOUND)
        );

        List<ShipResponse> playerShips = matchPlayer.getShips().stream()
                .map(shipConverter::toResponse)
                .toList();

        if (MatchStatus.WAITING.equals(match.getStatus()) || MatchStatus.PLANNING.equals(match.getStatus())) {
            return GameInfoResponse.builder()
                    .playerShips(playerShips)
                    .matchId(matchId)
                    .matchStatus(match.getStatus())
                    .matchPlayerId(matchPlayer.getId())
                    .isItMyTurn(false)
                    .build();
        }
        boolean isItMyTurn = match.getCurrentPlayerTurnId().equals(matchPlayer.getId());

        List<Move> allMoves = match.getMoves().stream().toList();
        List<MoveResponse> playerMoves = allMoves.stream()
                .filter(m -> m.getShooterId().equals(matchPlayer.getId()))
                .map(moveConverter::toResponse)
                .toList();
        List<MoveResponse> opponentMoves = allMoves.stream()
                .filter(m -> !m.getShooterId().equals(matchPlayer.getId()))
                .map(moveConverter::toResponse)
                .toList();

        return GameInfoResponse.builder()
                .playerShips(playerShips)
                .matchId(matchId)
                .matchStatus(match.getStatus())
                .matchPlayerId(matchPlayer.getId())
                .playerMoves(playerMoves)
                .matchStatus(match.getStatus())
                .opponentMoves(opponentMoves)
                .isItMyTurn(isItMyTurn)
                .build();
    }
}
