package com.chnu.seabattle.service.serviceImpl;

import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.entity.Move;
import com.chnu.seabattle.entity.MoveResult;
import com.chnu.seabattle.entity.Orientation;
import com.chnu.seabattle.entity.Ship;
import com.chnu.seabattle.entity.ShipType;
import com.chnu.seabattle.exception.GameRuleViolationException;
import com.chnu.seabattle.repository.ShipRepository;
import com.chnu.seabattle.service.GameService;
import com.chnu.seabattle.service.MatchPlayerService;
import com.chnu.seabattle.service.MatchService;
import com.chnu.seabattle.service.MoveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Point;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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

    private void validateTurn(Match match, UUID playerId) {
        if (!match.getCurrentPlayerTurnId().equals(playerId)) {
            throw new GameRuleViolationException(
                    "It's not the player's turn"
            );
        }
    }

    private void requireStatus(Match match, MatchStatus expected) {
        if (match.getStatus() != expected) {
            throw new GameRuleViolationException(
                    String.format("Action not allowed in state %s", match.getStatus())
            );
        }
    }

    private Set<Point> getOccupiedCells(Ship ship) {
        Set<Point> cells = new HashSet<>();
        int length = ship.getShipType().getSize();

        for (int i = 0; i < length; i++) {
            int cx = ship.getOrientation() == Orientation.HORIZONTAL
                    ? ship.getStartX() + i
                    : ship.getStartX();

            int cy = ship.getOrientation() == Orientation.VERTICAL
                    ? ship.getStartY() + i
                    : ship.getStartY();

            cells.add(new Point(cx, cy));
        }
        return cells;
    }

    private MatchPlayer getMatchPlayer(Match match, UUID playerId) {
        return match.getPlayers()
                .stream()
                .filter(mp -> mp.getId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Player not found in match"));
    }


    public boolean overlapsOrTouches(
            Ship existing,
            int x,
            int y,
            Orientation orientation,
            int length
    ) {
        Set<Point> existingCells = getOccupiedCells(existing);

        for (int i = 0; i < length; i++) {
            int cx = orientation == Orientation.HORIZONTAL ? x + i : x;
            int cy = orientation == Orientation.VERTICAL ? y + i : y;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Point p = new Point(cx + dx, cy + dy);
                    if (existingCells.contains(p)) {
                        return true; // overlap or adjacency detected
                    }
                }
            }
        }
        return false;
    }

    private boolean isWithinBounds(int x, int y) {
        return x >= 0 && x < 10 && y >= 0 && y < 10;
    }

    private void validateCoordinates(int x, int y, ShipType shipType, Orientation orientation) {
        if (!isWithinBounds(x, y)) {
            throw new GameRuleViolationException("Coordinates out of bounds");
        }

        if (orientation == Orientation.HORIZONTAL) {
            if (x + shipType.getSize() > 10) {
                throw new GameRuleViolationException("Ship exceeds board boundaries horizontally");
            }
        } else {
            if (y + shipType.getSize() > 10) {
                throw new GameRuleViolationException("Ship exceeds board boundaries vertically");
            }
        }
    }


    private void validatePlacement(
            MatchPlayer player,
            ShipType type,
            int x,
            int y,
            Orientation orientation
    ) {
        List<Ship> ships = player.getShips();

        validateCoordinates(x, y, type, orientation);
        ships.forEach(ship -> {
            if (overlapsOrTouches(ship, x, y, orientation, type.getSize())) {
                throw new GameRuleViolationException(
                        "Ship placement overlaps or touches another ship"
                );
            }
        });

    }


    @Transactional
    @Override
    public Ship placeShip(Long matchId, UUID playerId, ShipType type, int startX, int startY, Orientation orientation) {
        MatchPlayer player = matchPlayerService.findById(playerId).orElseThrow(
                () -> new NoSuchElementException("Player not found")
        );

        if (player.isReady()) {
            throw new GameRuleViolationException("Cannot place ships after marking ready");
        }

        List<Ship> updatedShips = player.getShips();

        validatePlacement(player, type, startX, startY, orientation);

        Map<ShipType, Long> shipTypeCount = player.getShips().stream()
                .collect(
                        Collectors.groupingBy(Ship::getShipType, counting())
                );

        if (shipTypeCount.getOrDefault(type, 0L) >= type.allowedCount()) {
            throw new GameRuleViolationException(
                    String.format("Cannot place more ships of type %s", type)
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

    @Transactional
    @Override
    public Ship moveShip(Long matchId, UUID playerId, Long shipId, int x, int y, Orientation orientation) {
        Match match = matchService.findById(matchId).orElseThrow(
                () -> new NoSuchElementException("Match not found")
        );

        MatchPlayer player = getMatchPlayer(match, playerId);

        if (player.isReady()) {
            throw new GameRuleViolationException("Cannot move ships after marking ready");
        }

        List<Ship> ships = player.getShips();
        Ship ship = ships.stream().filter(s -> s.getId().equals(shipId)).findFirst().orElseThrow(
                () -> new NoSuchElementException("Ship not found")
        );

        ships.remove(ship);
        validatePlacement(player, ship.getShipType(), x, y, orientation);
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
                () -> new NoSuchElementException("Match not found")
        );
        requireStatus(match, MatchStatus.PLANNING);
        MatchPlayer player = getMatchPlayer(match, playerId);
        player.setReady(true);
        if (matchPlayerService.areAllPlayersReady(matchId)) {
            match.setStatus(MatchStatus.IN_PROGRESS);

            Random random = new Random();
            MatchPlayer randomPlayer = match.getPlayers().get(random.nextInt(match.getPlayers().size()));
            match.setCurrentPlayerTurnId(randomPlayer.getId());
        }

        return match;
    }

    @Transactional
    @Override
    public MoveResult fire(Long matchId, UUID shooterId, int x, int y) {
        Match match = matchService.findById(matchId).orElseThrow(
                () -> new NoSuchElementException("Match not found")
        );
        MatchPlayer player = getMatchPlayer(match, shooterId);
        requireStatus(match, MatchStatus.IN_PROGRESS);
        validateTurn(match, shooterId);
        if (!isWithinBounds(x, y)) {
            throw new GameRuleViolationException("Firing coordinates out of bounds");
        }

        if (moveService.existsByMatchIdAndShooterIdAndTargetXAndTargetY(matchId, shooterId, x, y)) {
            throw new GameRuleViolationException("Cannot fire at the same coordinates twice");
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
                .orElseThrow(() -> new NoSuchElementException("Opponent not found"));

        Optional<Ship> hitShipOpt = opponent.getShips().stream()
                .filter(ship -> getOccupiedCells(ship).contains(new Point(x, y)))
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
        return move.getMoveResult();

    }

    @Override
    public void handleDisconnect(Long matchId, UUID playerId) {
        Match match = matchService.findById(matchId).orElseThrow(
                () -> new NoSuchElementException("Match not found")
        );
        MatchPlayer player = getMatchPlayer(match, playerId);
        player.setConnected(false);
        player.setLastSeenAt(Instant.now());

//        UUID opponentId = getOpponentPlayerId(match, playerId);

        // Optional: notify opponent via WebSocket
//        webSocketService.handleDisconnect(matchId, opponentId);
    }

    @Override
    public void handleReconnect(Long matchId, UUID playerId) {
        Match match = matchService.findById(matchId).orElseThrow(
                () -> new NoSuchElementException("Match not found")
        );
        MatchPlayer player = getMatchPlayer(match, playerId);
        player.setConnected(true);
        player.setLastSeenAt(Instant.now());

//        UUID opponentId = getOpponentPlayerId(match, playerId);

        // Optional: notify opponent via WebSocket
        // websocketService.notifyPlayerReconnected(player.getMatch(), player);
    }

    @Override
    @Transactional(readOnly = true)
    public UUID getOpponentPlayerId(Match match, UUID playerId) {
        List<MatchPlayer> players = match.getPlayers();
        if (players.size() != 2) {
            throw new GameRuleViolationException("Match must have exactly 2 players");
        }

        return players.stream()
                .filter(p -> !p.getId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Opponent not found"))
                .getId();
    }
}
