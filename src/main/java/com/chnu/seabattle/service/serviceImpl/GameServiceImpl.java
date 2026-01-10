package com.chnu.seabattle.service.serviceImpl;

import com.chnu.seabattle.entity.*;
import com.chnu.seabattle.exception.GameRuleViolationException;
import com.chnu.seabattle.repository.MatchPlayerRepository;
import com.chnu.seabattle.repository.MatchRepository;
import com.chnu.seabattle.repository.MoveRepository;
import com.chnu.seabattle.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {

    private final MatchRepository matchRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final MoveRepository moveRepository;

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

        if (ships == null) {
            ships = new ArrayList<>();
            player.setShips(ships);
        }

        validateCoordinates(x, y, type, orientation);
        Map<ShipType, Long> shipTypeCount = ships.stream()
                .collect(
                        Collectors.groupingBy(
                                Ship::getShipType,
                                java.util.stream.Collectors.counting()
                        )
                );
        if (shipTypeCount.getOrDefault(type, 0L) >= type.AllowedCount()) {
            throw new GameRuleViolationException(
                    String.format("Cannot place more ships of type %s", type)
            );
        }
        ships.forEach(ship -> {
            if (overlapsOrTouches(ship, x, y, orientation, type.getSize())) {
                throw new GameRuleViolationException(
                        "Ship placement overlaps or touches another ship"
                );
            }
        });

    }


    @Override
    public void placeShip(Long matchId, UUID playerId, ShipType type, int startX, int startY, Orientation orientation) {
        Match match = matchRepository.findById(matchId).orElseThrow(
                () -> new NoSuchElementException("Match not found")
        );
//        requireStatus(match, MatchStatus.PLANNING);
        MatchPlayer player = getMatchPlayer(match, playerId);

        if (player.isReady()) {
            throw new GameRuleViolationException("Cannot place ships after marking ready");
        }

        validatePlacement(player, type, startX, startY, orientation);
        Ship ship = Ship.builder()
                .matchPlayer(player)
                .shipType(type)
                .startX(startX)
                .startY(startY)
                .orientation(orientation)
                .hits(0)
                .isSunk(false)
                .build();

        // Initialize ships list if null
        List<Ship> updatedShips = player.getShips();
        if (updatedShips == null) {
            updatedShips = new ArrayList<>();
            player.setShips(updatedShips);
        }

        updatedShips.add(ship);
        matchPlayerRepository.save(player);
    }

    @Override
    public Match markReady(Long matchId, UUID playerId) {
        Match match = matchRepository.findById(matchId).orElseThrow(
                () -> new NoSuchElementException("Match not found")
        );
        requireStatus(match, MatchStatus.PLANNING);
        MatchPlayer player = getMatchPlayer(match, playerId);
        player.setReady(true);
        boolean allReady = match.getPlayers()
                .stream()
                .allMatch(MatchPlayer::isReady);
        if (allReady) {
            match.setStatus(MatchStatus.IN_PROGRESS);

            Random random = new Random();
            MatchPlayer randomPlayer = match.getPlayers().get(random.nextInt(match.getPlayers().size()));
            match.setCurrentPlayerTurnId(randomPlayer.getId());
        }
        matchRepository.save(match);
        matchPlayerRepository.save(player);

        return match;
    }

    @Override
    public MoveResult fire(Long matchId, UUID shooterId, int x, int y) {
        Match match = matchRepository.findById(matchId).orElseThrow(
                () -> new NoSuchElementException("Match not found")
        );
        MatchPlayer player = getMatchPlayer(match, shooterId);
        requireStatus(match, MatchStatus.IN_PROGRESS);
        validateTurn(match, shooterId);
        if (!isWithinBounds(x, y)) {
            throw new GameRuleViolationException("Firing coordinates out of bounds");
        }
        List<Move> moves = match.getMoves()
                .stream()
                .filter(move -> move.getShooterId().equals(shooterId))
                .toList();

        for (Move move : moves) {
            if (move.getTargetX() == x && move.getTargetY() == y) {
                throw new GameRuleViolationException("Cannot fire at the same coordinates twice");
            }
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
        moveRepository.save(move);
        matchRepository.save(match);
        return move.getMoveResult();

    }

    @Override
    @Transactional
    public void handleDisconnect(Long matchId, UUID playerId) {
        Match match = matchRepository.findById(matchId).orElseThrow(
                () -> new NoSuchElementException("Match not found")
        );
        MatchPlayer player = getMatchPlayer(match, playerId);
        player.setConnected(false);
        player.setLastSeenAt(Instant.now());
        matchPlayerRepository.save(player);

//        UUID opponentId = getOpponentPlayerId(match, playerId);

        // Optional: notify opponent via WebSocket
//        webSocketService.handleDisconnect(matchId, opponentId);
    }

    @Override
    public void handleReconnect(Long matchId, UUID playerId) {
        Match match = matchRepository.findById(matchId).orElseThrow(
                () -> new NoSuchElementException("Match not found")
        );
        MatchPlayer player = getMatchPlayer(match, playerId);
        player.setConnected(true);
        player.setLastSeenAt(Instant.now());
        matchPlayerRepository.save(player);

//        UUID opponentId = getOpponentPlayerId(match, playerId);

        // Optional: notify opponent via WebSocket
        // websocketService.notifyPlayerReconnected(player.getMatch(), player);
    }

    @Override
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
