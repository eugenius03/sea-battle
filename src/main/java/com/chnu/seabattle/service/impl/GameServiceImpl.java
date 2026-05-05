package com.chnu.seabattle.service.impl;

import com.chnu.seabattle.config.DroneConfig;
import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.converter.MoveConverter;
import com.chnu.seabattle.converter.ShipConverter;
import com.chnu.seabattle.dto.game.GameInfoResponse;
import com.chnu.seabattle.dto.move.MoveRequest;
import com.chnu.seabattle.dto.move.MoveResponse;
import com.chnu.seabattle.dto.ship.ShipRequest;
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
import com.chnu.seabattle.exception.ServerErrorException;
import com.chnu.seabattle.repository.ShipRepository;
import com.chnu.seabattle.service.GameService;
import com.chnu.seabattle.service.MatchPlayerService;
import com.chnu.seabattle.service.MatchService;
import com.chnu.seabattle.service.MoveService;
import com.chnu.seabattle.service.WebSocketService;
import com.chnu.seabattle.service.strategy.MoveStrategy;
import com.chnu.seabattle.util.BoardUtils;
import com.chnu.seabattle.util.MatchUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.counting;

@Service
@RequiredArgsConstructor
@Slf4j
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
    private final DroneConfig droneConfig;


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
    public Ship placeShip(String inviteToken, UUID playerId, ShipRequest shipRequest) {
        MatchPlayer player = validateAndGetPlayer(inviteToken, playerId);

        List<Ship> updatedShips = player.getShips();

        if (!BoardUtils.isValidPlacement(updatedShips, shipRequest.shipType(), shipRequest.startX(),
                shipRequest.startY(), shipRequest.orientation())) {
            throw new GameRuleViolationException(ErrorConstants.INVALID_SHIP_PLACEMENT);
        }

        Map<ShipType, Long> shipTypeCount = player.getShips().stream()
                .collect(
                        Collectors.groupingBy(Ship::getShipType, counting())
                );

        if (shipTypeCount.getOrDefault(shipRequest.shipType(), 0L) >= shipRequest.shipType().allowedCount()) {
            throw new GameRuleViolationException(
                    String.format(ErrorConstants.SHIP_TYPE_LIMIT, shipRequest.shipType())
            );
        }

        Ship ship = shipConverter.toEntity(shipRequest);
        ship.setMatchPlayer(player);

        ship = shipRepository.save(ship);

        updatedShips.add(ship);

        return ship;
    }

    @Override
    @Transactional
    public List<Ship> generateRandomShips(String inviteToken, UUID playerId) {
        MatchPlayer player = validateAndGetPlayer(inviteToken, playerId);

        List<Ship> generatedBoard = generateValidBoardLayout(player);

        updateOrSaveExistingShips(player, generatedBoard);

        return player.getShips().stream()
                .toList();
    }

    private MatchPlayer validateAndGetPlayer(String inviteToken, UUID playerId) {
        MatchPlayer player = matchPlayerService.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorConstants.PLAYER_NOT_FOUND));

        Match match = matchService.findByInviteTokenForGame(inviteToken)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorConstants.MATCH_NOT_FOUND));

        MatchUtils.requireStatuses(match, MatchStatus.WAITING, MatchStatus.PLANNING);

        if (player.isReady()) {
            throw new GameRuleViolationException(ErrorConstants.PLAYER_ALREADY_READY);
        }

        return player;
    }

    private void updateOrSaveExistingShips(MatchPlayer player, List<Ship> generatedBoard) {
        List<Ship> existing = player.getShips();

        if (existing.isEmpty()) {
            existing.addAll(generatedBoard);
            shipRepository.saveAll(existing);
            return;
        }

        for (int i = 0; i < existing.size(); i++) {
            Ship src = generatedBoard.get(i);
            Ship dst = existing.get(i);

            dst.setShipType(src.getShipType());
            dst.setStartX(src.getStartX());
            dst.setStartY(src.getStartY());
            dst.setOrientation(src.getOrientation());
            dst.setHits(0);
            dst.setSunk(false);

            if (i == existing.size() - 1 && existing.size() < generatedBoard.size()) {
                existing.addAll(
                        generatedBoard.stream()
                                .skip(i + 1L)
                                .toList()
                );
                shipRepository.saveAll(existing);
                return;
            }
        }
    }

    private List<Ship> generateValidBoardLayout(MatchPlayer player) {
        List<ShipType> sortedShipTypes = Arrays.stream(ShipType.values())
                .sorted(Comparator.comparingInt(ShipType::getSize).reversed())
                .toList();

        int maxBoardRetries = 50;

        for (int attempt = 0; attempt < maxBoardRetries; attempt++) {
            List<Ship> board = new ArrayList<>();

            if (tryPopulateFullBoard(board, sortedShipTypes, player)) {
                return board;
            }
        }

        throw new ServerErrorException(ErrorConstants.SHIP_GENERATION_FAILED);
    }

    private boolean tryPopulateFullBoard(List<Ship> board, List<ShipType> types, MatchPlayer player) {
        for (ShipType type : types) {
            for (int i = 0; i < type.allowedCount(); i++) {
                if (!tryPlaceSingleShip(board, type, player)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean tryPlaceSingleShip(List<Ship> board, ShipType type, MatchPlayer player) {
        for (int attempt = 0; attempt < MAX_PLACEMENT_ATTEMPTS; attempt++) {
            int x = random.nextInt(GRID_SIZE);
            int y = random.nextInt(GRID_SIZE);
            Orientation orientation = random.nextBoolean()
                    ? Orientation.HORIZONTAL : Orientation.VERTICAL;

            if (BoardUtils.isValidPlacement(board, type, x, y, orientation)) {
                board.add(Ship.builder()
                        .matchPlayer(player)
                        .shipType(type)
                        .startX(x)
                        .startY(y)
                        .orientation(orientation)
                        .hits(0)
                        .isSunk(false)
                        .build()
                );
                return true;
            }
        }
        return false;
    }

    @Transactional
    @Override
    public Ship moveShip(String inviteToken, UUID playerId, Long shipId, int x, int y, Orientation orientation) {

        MatchPlayer player = validateAndGetPlayer(inviteToken, playerId);

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
    public Match markReady(String inviteToken, MatchPlayer player) {
        Match match = matchService.getByInviteToken(inviteToken);
        MatchUtils.requireStatuses(match, MatchStatus.PLANNING);

        if (player.isReady()) {
            throw new GameRuleViolationException(ErrorConstants.PLAYER_ALREADY_READY);
        }

        player.setReady(true);
        boolean allReady = match.getPlayers().stream().allMatch(MatchPlayer::isReady);
        if (allReady) {
            match.setStatus(MatchStatus.IN_PROGRESS);

            MatchPlayer randomPlayer = match.getPlayers().get(random.nextInt(match.getPlayers().size()));
            match.setCurrentPlayerTurnId(randomPlayer.getId());

            webSocketService.updateMatchStatus(inviteToken, MatchStatus.IN_PROGRESS, match.getCurrentPlayerTurnId(), null);
        }

        UUID opponentId = getOpponentPlayerId(match, player.getId());

        webSocketService.sendPlayerReadyMessage(inviteToken, opponentId);
        log.info("All players ready. Match {} is now IN_PROGRESS. First turn: {}", inviteToken, match.getCurrentPlayerTurnId());
        return match;
    }

    @Transactional
    @Override
    public List<Move> executeMove(String inviteToken, UUID shooterId, MoveRequest moveRequest) {
        Match match = matchService.findByInviteTokenForGame(inviteToken).orElseThrow(
                () -> new ResourceNotFoundException(ErrorConstants.MATCH_NOT_FOUND)
        );
        MatchUtils.requireStatuses(match, MatchStatus.IN_PROGRESS);
        if (!isPlayerTurn(match, shooterId)) {
            log.warn("Rule violation: Player {} attempted to fire out of turn in match {}",
                    shooterId, match.getInviteToken());
            throw new GameRuleViolationException(ErrorConstants.NOT_PLAYERS_TURN);
        }

        if (!BoardUtils.isWithinBounds(moveRequest.x(), moveRequest.y())) {
            log.warn("Rule violation: Player {} attempted to fire out of bounds in match {}",
                    shooterId, match.getInviteToken());
            throw new GameRuleViolationException(ErrorConstants.COORDINATES_OUT_OF_BOUNDS);
        }

        if (moveService.existsByMatchIdAndShooterIdAndTargetXAndTargetYAndMoveType(
                match.getId(), shooterId, moveRequest.x(), moveRequest.y(), moveRequest.moveType())) {
            log.warn("Rule violation: Player {} attempted to fire at the same coordinates with the same MoveType {}",
                    shooterId, moveRequest.moveType());
            throw new GameRuleViolationException(ErrorConstants.DUPLICATE_FIRE);
        }

        MoveStrategy strategy = strategies.get(moveRequest.moveType());
        if (strategy == null) {
            log.warn("Rule violation: Player {} attempted to fire with unknown MoveType {}",
                    shooterId, moveRequest.moveType());
            throw new GameRuleViolationException(
                    String.format(ErrorConstants.UNKNOWN_MOVE_TYPE, moveRequest.moveType())
            );
        }

        List<Move> result = strategy.execute(match, shooterId, moveRequest);
        UUID opponentId = getOpponentPlayerId(match, shooterId);

        boolean isItOpponentsTurn = opponentId.equals(match.getCurrentPlayerTurnId());

        webSocketService.sendOpponentMoveMessage(
                match.getInviteToken(), opponentId,
                result.stream().map(moveConverter::toResponse).toList(),
                isItOpponentsTurn
        );

        log.info("Player {} fired at [{}, {}] in match {} using moveType {}",
                shooterId, moveRequest.x(), moveRequest.y(), match.getInviteToken(), moveRequest.moveType()
        );

        if (MatchStatus.FINISHED.equals(match.getStatus())) {
            List<ShipResponse> winnerShips = getMatchPlayer(match, shooterId).getShips().stream()
                    .map(shipConverter::toResponse)
                    .toList();

            webSocketService.updateMatchStatus(
                    match.getInviteToken(), MatchStatus.FINISHED,
                    match.getCurrentPlayerTurnId(), winnerShips);
            log.info("Match {} FINISHED. Winner: {}", match.getInviteToken(), shooterId);
        }
        return result;
    }

    @Override
    @Transactional
    public void handleDisconnect(String inviteToken, UUID matchPlayerId) {
        Match match = matchService.findByInviteTokenForGame(inviteToken).orElseThrow(
                () -> new ResourceNotFoundException(ErrorConstants.MATCH_NOT_FOUND)
        );
        MatchPlayer player = getMatchPlayer(match, matchPlayerId);
        player.setConnected(false);
        player.setLastSeenAt(Instant.now());
        log.info("Player {} disconnected from match {}", matchPlayerId, inviteToken);
    }

    @Override
    @Transactional
    public void handleReconnect(Match match, MatchPlayer player) {
        player.setConnected(true);
        player.setLastSeenAt(Instant.now());
        log.info("Player {} reconnected to match {}", player.getId(), match.getInviteToken());
        webSocketService.handleReconnect(match.getInviteToken(), player.getId());
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
    public GameInfoResponse getMatchInfo(Match match, MatchPlayer matchPlayer) {

        List<ShipResponse> playerShips = matchPlayer.getShips().stream()
                .map(shipConverter::toResponse)
                .toList();

        Map<MoveType, Long> uses = Arrays.stream(MoveType.values())
                .filter(val -> !MoveType.STANDARD.equals(val))
                .collect(Collectors.toMap(
                                val -> val,
                                val ->
                                        droneConfig.getMaxUsagesFor(val) -
                                                moveService.countUsagesForMoveType(match.getId(), matchPlayer.getId(), val)
                        )
                );

        if (MatchStatus.WAITING.equals(match.getStatus()) || MatchStatus.PLANNING.equals(match.getStatus())) {
            return GameInfoResponse.builder()
                    .playerShips(playerShips)
                    .matchStatus(match.getStatus())
                    .matchPlayerId(matchPlayer.getId())
                    .isItMyTurn(false)
                    .droneUsage(uses)
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
                .matchStatus(match.getStatus())
                .matchPlayerId(matchPlayer.getId())
                .playerMoves(playerMoves)
                .opponentMoves(opponentMoves)
                .droneUsage(uses)
                .isItMyTurn(isItMyTurn)
                .build();
    }
}
