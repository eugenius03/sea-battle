package com.chnu.seabattle.service.impl;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.converter.MoveConverter;
import com.chnu.seabattle.converter.ShipConverter;
import com.chnu.seabattle.dto.Cell;
import com.chnu.seabattle.dto.FireResult;
import com.chnu.seabattle.dto.GameInfoResponse;
import com.chnu.seabattle.dto.move.MoveResponse;
import com.chnu.seabattle.dto.ship.ShipResponse;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.entity.Move;
import com.chnu.seabattle.entity.MoveResult;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.counting;

@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {

    private final MatchService matchService;
    private final MoveService moveService;
    private final ShipRepository shipRepository;
    private final MatchPlayerService matchPlayerService;
    private final Random random = new Random();
    private static final int GRID_SIZE = 10;
    private static final int MAX_PLACEMENT_ATTEMPTS = 100;
    private final MoveConverter moveConverter;
    private final ShipConverter shipConverter;
    private final WebSocketService webSocketService;

    private boolean isPlayerTurn(Match match, UUID playerId) {
        return match.getCurrentPlayerTurnId().equals(playerId);
    }

    private void requireStatuses(Match match, MatchStatus... expected) {
        boolean isValid = Arrays.stream(expected)
                .anyMatch(status -> status.equals(match.getStatus()));

        if (!isValid) {
            throw new GameRuleViolationException(
                    String.format(ErrorConstants.INVALID_MATCH_STATE, match.getStatus())
            );
        }
    }

    private Set<Cell> getOccupiedCells(Ship ship) {
        Set<Cell> cells = new HashSet<>();
        int length = ship.getShipType().getSize();

        for (int i = 0; i < length; i++) {
            int cx = ship.getOrientation() == Orientation.HORIZONTAL
                    ? ship.getStartX() + i
                    : ship.getStartX();

            int cy = ship.getOrientation() == Orientation.VERTICAL
                    ? ship.getStartY() + i
                    : ship.getStartY();

            cells.add(new Cell(cx, cy));
        }
        return cells;
    }

    private MatchPlayer getMatchPlayer(Match match, UUID playerId) {
        return match.getPlayers()
                .stream()
                .filter(mp -> mp.getId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorConstants.PLAYER_NOT_FOUND));
    }


    private boolean isWithinBounds(int x, int y, ShipType type, Orientation orientation) {
        if (x < 0 || y < 0) return false;
        return Orientation.HORIZONTAL.equals(orientation)
                ? x + type.getSize() <= GRID_SIZE
                : y + type.getSize() <= GRID_SIZE;
    }

    private boolean isWithinBounds(int x, int y) {
        return x >= 0 && x < GRID_SIZE && y >= 0 && y < GRID_SIZE;
    }

    private boolean isValidCoordinates(int x, int y, ShipType type, Orientation orientation) {
        return isWithinBounds(x, y, type, orientation);
    }

    private boolean isValidPlacement(List<Ship> ships, ShipType type, int x, int y, Orientation orientation) {
        if (!isValidCoordinates(x, y, type, orientation)) {
            return false;
        }
        return ships.stream().noneMatch(ship ->
                overlapsOrTouches(ship, x, y, orientation, type.getSize())
        );
    }

    private boolean overlapsOrTouches(Ship existing, int x, int y, Orientation orientation, int length) {
        Set<Cell> existingCells = getOccupiedCells(existing);

        for (int i = 0; i < length; i++) {
            int cx = orientation == Orientation.HORIZONTAL ? x + i : x;
            int cy = orientation == Orientation.VERTICAL ? y + i : y;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (existingCells.contains(new Cell(cx + dx, cy + dy))) return true;
                }
            }
        }
        return false;
    }


    @Transactional
    @Override
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

        if (!isValidPlacement(updatedShips, type, startX, startY, orientation)) {
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

                    if (!isValidPlacement(player.getShips(), type, x, y, orientation)) continue;

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
        if (!isValidPlacement(ships, ship.getShipType(), x, y, orientation)) {
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
    public FireResult fire(Long matchId, UUID shooterId, int x, int y) {
        Match match = matchService.findByIdForGame(matchId).orElseThrow(
                () -> new ResourceNotFoundException(ErrorConstants.MATCH_NOT_FOUND)
        );
        MatchPlayer player = getMatchPlayer(match, shooterId);
        requireStatuses(match, MatchStatus.IN_PROGRESS);

        if (!isPlayerTurn(match, shooterId)) {
            throw new GameRuleViolationException(ErrorConstants.NOT_PLAYERS_TURN);
        }
        if (!isWithinBounds(x, y)) {
            throw new GameRuleViolationException(ErrorConstants.COORDINATES_OUT_OF_BOUNDS);
        }

        if (moveService.existsByMatchIdAndShooterIdAndTargetXAndTargetY(matchId, shooterId, x, y)
        ) {
            throw new GameRuleViolationException(ErrorConstants.DUPLICATE_FIRE);
        }

        Move move = Move.builder()
                .match(match)
                .shooterId(shooterId)
                .targetX(x)
                .targetY(y)
                .build();

        MatchPlayer opponent = match.getPlayers().stream()
                .filter(mp -> !mp.getId().equals(player.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorConstants.PLAYER_NOT_FOUND));

        Optional<Ship> hitShipOpt = opponent.getShips().stream()
                .filter(ship -> getOccupiedCells(ship).contains(new Cell(x, y)))
                .findFirst();

        if (hitShipOpt.isPresent()) {
            Ship hitShip = hitShipOpt.get();
            hitShip.setHits(hitShip.getHits() + 1);

            if (hitShip.getHits() == hitShip.getShipType().getSize()) {
                hitShip.setSunk(true);
                move.setMoveResult(MoveResult.SUNK);

                boolean allSunk = opponent.getShips().stream().allMatch(Ship::isSunk);
                if (allSunk) {
                    match.setStatus(MatchStatus.FINISHED);
                    match.setWinnerId(shooterId);
                    match.setFinishedAt(Instant.now());
                    move.setMoveResult(MoveResult.FINISHED);
                }
            } else {
                move.setMoveResult(MoveResult.HIT);
            }
        } else {
            move.setMoveResult(MoveResult.MISS);
            match.setCurrentPlayerTurnId(
                    opponent.getId()
            );

        }
        moveService.create(move);
        return new FireResult(move.getMoveResult(), match);

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
