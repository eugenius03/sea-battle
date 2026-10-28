package com.chnu.seabattle.service;

import com.chnu.seabattle.config.DroneConfig;
import com.chnu.seabattle.constants.BotConstants;
import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.dto.game.Cell;
import com.chnu.seabattle.dto.move.MoveRequest;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.entity.Move;
import com.chnu.seabattle.entity.MoveType;
import com.chnu.seabattle.entity.Orientation;
import com.chnu.seabattle.entity.ShipType;
import com.chnu.seabattle.entity.User;
import com.chnu.seabattle.enums.CellState;
import com.chnu.seabattle.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;

import static com.chnu.seabattle.constants.NumericalConstants.GRID_SIZE;

@Service
@RequiredArgsConstructor
public class GameBotService {
    private final GameService gameService;
    private final MatchService matchService;
    private final UserService userService;
    private final MoveService moveService;
    private final DroneConfig droneConfig;
    SecureRandom random = new SecureRandom();

    private static final List<MoveType> DRONE_PRIORITY = List.of(MoveType.SURVEILLANCE_DRONE, MoveType.ATTACK_DRONE);

    @Transactional
    public Match createBotMatch(UUID humanUserId) {
        User botUser = userService.findByUsername(BotConstants.BOT_USERNAME)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorConstants.USER_NOT_FOUND));
        Match match = matchService.createMatch(humanUserId, botUser.getId());
        MatchPlayer botPlayer = match.getPlayers().stream()
                .filter(player -> botUser.getId().equals(player.getUserId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorConstants.PLAYER_NOT_FOUND));
        gameService.generateRandomShips(botPlayer);
        gameService.markReady(botPlayer);
        return match;
    }

    private boolean isWithinBoard(int start, Orientation orientation, int length, int width, int height) {
        if (orientation == Orientation.HORIZONTAL) {
            return start + length <= width;
        } else {
            return start + length <= height;
        }
    }

    private int checkDirection(CellState[][] stateGrid, Orientation orientation, int startX, int startY, int sign) {
        int dx = orientation == Orientation.HORIZONTAL ? 1 : 0;
        int dy = orientation == Orientation.VERTICAL ? 1 : 0;
        int height = stateGrid.length;
        int width = stateGrid[0].length;
        int x = startX + sign * dx;
        int y = startY + sign * dy;
        int result = 0;
        while (y >= 0 && y < height && x >= 0 && x < width && stateGrid[y][x] == CellState.HIT) {
            result += 1;
            stateGrid[y][x] = CellState.BLOCKED;
            x = x + sign * dx;
            y = y + sign * dy;
        }
        return result;
    }

    CellState[][] buildBoard(List<Move> moves) {
        CellState[][] board = new CellState[GRID_SIZE][GRID_SIZE];
        for (CellState[] row : board) {
            Arrays.fill(row, CellState.UNSHOT);
        }
        moves.forEach(move -> board[move.getTargetY()][move.getTargetX()] = switch (move.getMoveResult()) {
            case SURVEILLANCE -> CellState.SURVEILLANCE;
            case HIT -> CellState.HIT;
            case MISS -> CellState.MISS;
            case SUNK, FINISHED -> CellState.SUNK;
        });
        return board;
    }

    List<Integer> findSunk(CellState[][] stateGrid) {
        int height = stateGrid.length;
        int width = stateGrid[0].length;
        List<Integer> result = new ArrayList<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (stateGrid[y][x] == CellState.SUNK) {
                    stateGrid[y][x] = CellState.BLOCKED;
                    int shipSize = 1;
                    for (Orientation orientation : Orientation.values()) {
                        shipSize += checkDirection(stateGrid, orientation, x, y, 1);
                        shipSize += checkDirection(stateGrid, orientation, x, y, -1);
                    }
                    result.add(shipSize);
                }
            }
        }
        return result;
    }

    void blockDiagonal(CellState[][] stateGrid) {
        int height = stateGrid.length;
        int width = stateGrid[0].length;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isShipCell(stateGrid[y][x])) {
                    for (int dy = -1; dy <= 1; dy += 2) {
                        for (int dx = -1; dx <= 1; dx += 2) {
                            int ny = y + dy;
                            int nx = x + dx;
                            if (ny >= 0 && ny < height && nx >= 0 && nx < width
                                    && stateGrid[ny][nx] == CellState.UNSHOT) {
                                stateGrid[ny][nx] = CellState.BLOCKED;
                            }
                        }
                    }
                }
            }
        }
    }

    int[][] countCells(CellState[][] stateGrid, List<Integer> remainingShips) {
        int height = stateGrid.length;
        int width = stateGrid[0].length;
        int[][] weights = new int[height][width];

        for (int shipLength : remainingShips) {
            for (Orientation orientation : orientationsFor(shipLength)) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int start = orientation == Orientation.HORIZONTAL ? x : y;
                        if (isWithinBoard(start, orientation, shipLength, width, height)) {
                            addPosition(stateGrid, weights, x, y, orientation, shipLength);
                        }
                    }
                }
            }
        }
        return weights;
    }

    List<Cell> bestCells(CellState[][] stateGrid, int[][] board) {
        int height = stateGrid.length;
        int width = stateGrid[0].length;
        List<Cell> revealed = IntStream.range(0, height).boxed()
                .flatMap(y -> IntStream.range(0, width)
                        .filter(x -> stateGrid[y][x] == CellState.SURVEILLANCE)
                        .mapToObj(x -> new Cell(x, y)))
                .toList();
        if (!revealed.isEmpty()) {
            return revealed;
        }
        int max = IntStream.range(0, height)
                .flatMap(y -> IntStream.range(0, width)
                        .filter(x -> stateGrid[y][x] == CellState.UNSHOT)
                        .map(x -> board[y][x]))
                .max()
                .orElseThrow();

        return IntStream.range(0, height).boxed()
                .flatMap(y -> IntStream.range(0, width)
                        .filter(x -> stateGrid[y][x] == CellState.UNSHOT && board[y][x] == max)
                        .mapToObj(x -> new Cell(x, y)))
                .toList();
    }

    Cell pickCell(CellState[][] stateGrid, int[][] board) {
        List<Cell> best = bestCells(stateGrid, board);
        return best.get(random.nextInt(best.size()));
    }

    Optional<Cell> bestDroneCenter(CellState[][] stateGrid, int[][] weights) {
        int height = stateGrid.length;
        int width = stateGrid[0].length;
        int bestSum = -1;
        List<Cell> best = new ArrayList<>();
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (!isClearWindow(stateGrid, x, y)) {
                    continue;
                }
                int sum = windowSum(weights, x, y);
                if (sum > bestSum) {
                    bestSum = sum;
                    best.clear();
                }
                if (sum == bestSum) {
                    best.add(new Cell(x, y));
                }
            }
        }
        return best.isEmpty() ? Optional.empty() : Optional.of(best.get(random.nextInt(best.size())));
    }

    private boolean isClearWindow(CellState[][] stateGrid, int cx, int cy) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (stateGrid[cy + dy][cx + dx] != CellState.UNSHOT) {
                    return false;
                }
            }
        }
        return true;
    }

    private int windowSum(int[][] weights, int cx, int cy) {
        int sum = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                sum += weights[cy + dy][cx + dx];
            }
        }
        return sum;
    }

    private boolean isShipCell(CellState state) {
        return state == CellState.HIT || state == CellState.SURVEILLANCE;
    }

    private boolean isHuntMode(CellState[][] stateGrid) {
        return Arrays.stream(stateGrid).noneMatch(row -> Arrays.stream(row).anyMatch(this::isShipCell));
    }

    private int usesLeft(List<Move> moves, MoveType type, ToIntFunction<MoveType> limit) {
        long used = moves.stream()
                .filter(m -> m.getMoveType() == type && m.isMoveOrigin())
                .count();
        return (int) Math.max(0, limit.applyAsInt(type) - used);
    }

    private Optional<MoveRequest> tryDrone(MoveType type, CellState[][] stateGrid, int[][] weights,
                                           List<Move> moves, ToIntFunction<MoveType> limit) {
        if (!isHuntMode(stateGrid) || usesLeft(moves, type, limit) == 0) {
            return Optional.empty();
        }
        return bestDroneCenter(stateGrid, weights)
                .map(c -> new MoveRequest(c.x(), c.y(), type));
    }

    private List<Orientation> orientationsFor(int shipLength) {
        return shipLength == 1
                ? List.of(Orientation.HORIZONTAL)
                : List.of(Orientation.HORIZONTAL, Orientation.VERTICAL);
    }

    private void addPosition(CellState[][] stateGrid, int[][] weights, int x, int y,
                             Orientation orientation, int shipLength) {
        int dx = orientation == Orientation.HORIZONTAL ? 1 : 0;
        int dy = orientation == Orientation.VERTICAL ? 1 : 0;

        boolean coversHit = false;
        for (int i = 0; i < shipLength; i++) {
            CellState state = stateGrid[y + i * dy][x + i * dx];
            if (state == CellState.BLOCKED || state == CellState.MISS) {
                return;
            }
            if (isShipCell(state)) {
                coversHit = true;
            }
        }

        int score = coversHit ? 1 + BotConstants.BOOST_VALUE : 1;
        for (int i = 0; i < shipLength; i++) {
            weights[y + i * dy][x + i * dx] += score;
        }
    }

    List<Integer> remainingFleet(List<Integer> sunkShips) {
        List<Integer> remaining = new ArrayList<>();
        for (ShipType type : ShipType.values()) {
            for (int i = 0; i < type.allowedCount(); i++) {
                remaining.add(type.getSize());
            }
        }
        sunkShips.forEach(remaining::remove);
        return remaining;
    }

    public MoveRequest chooseShot(List<Move> moves, ToIntFunction<MoveType> limit) {
        CellState[][] stateGrid = buildBoard(moves);
        List<Integer> sunkShips = findSunk(stateGrid);
        List<Integer> remainingFleet = remainingFleet(sunkShips);
        blockDiagonal(stateGrid);
        int[][] weights = countCells(stateGrid, remainingFleet);
        return DRONE_PRIORITY.stream()
                .map(type -> tryDrone(type, stateGrid, weights, moves, limit))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseGet(() -> {
                    Cell cell = pickCell(stateGrid, weights);
                    return new MoveRequest(cell.x(), cell.y(), MoveType.STANDARD);
                });
    }

    @Transactional
    public void playTurn(String inviteToken) {
        play(inviteToken, droneConfig::getMaxUsagesFor);
    }

    @Transactional
    public void playFallbackTurn(String inviteToken) {
        play(inviteToken, type -> 0);
    }

    private void play(String inviteToken, ToIntFunction<MoveType> limit) {
        Match match = matchService.findByInviteTokenForGame(inviteToken)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid invite token: " + inviteToken));
        if (match.getStatus() != MatchStatus.IN_PROGRESS) {
            return;
        }
        match.getPlayers().stream()
                .filter(p -> p.getId().equals(match.getCurrentPlayerTurnId()))
                .findFirst()
                .filter(p -> userService.isBot(p.getUserId()))
                .ifPresent(bot -> {
                    List<Move> moves = moveService.findByMatchIdAndShooterIdOrderByIdAsc(match.getId(), bot.getId());
                    gameService.executeMove(bot, chooseShot(moves, limit));
                });
    }
}
