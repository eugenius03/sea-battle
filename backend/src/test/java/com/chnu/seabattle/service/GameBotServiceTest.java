package com.chnu.seabattle.service;

import com.chnu.seabattle.config.DroneConfig;
import com.chnu.seabattle.constants.BotConstants;
import com.chnu.seabattle.dto.game.Cell;
import com.chnu.seabattle.dto.move.MoveRequest;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.entity.Move;
import com.chnu.seabattle.entity.MoveResult;
import com.chnu.seabattle.entity.MoveType;
import com.chnu.seabattle.entity.User;
import com.chnu.seabattle.enums.CellState;
import com.chnu.seabattle.enums.UserType;
import com.chnu.seabattle.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToIntFunction;

import static com.chnu.seabattle.constants.NumericalConstants.GRID_SIZE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("GameBotService - countCells Tests")
class GameBotServiceTest {

    private final GameService gameService = mock(GameService.class);
    private final MatchService matchService = mock(MatchService.class);
    private final UserService userService = mock(UserService.class);
    private final MoveService moveService = mock(MoveService.class);
    private final DroneConfig droneConfig = mock(DroneConfig.class);
    private final GameBotService bot = new GameBotService(gameService, matchService, userService, moveService, droneConfig);

    private static CellState[][] grid(int height, int width) {
        CellState[][] grid = new CellState[height][width];
        for (CellState[] row : grid) {
            Arrays.fill(row, CellState.UNSHOT);
        }
        return grid;
    }

    @Test
    @DisplayName("One ship of length 3 on an empty 1x7 row")
    void emptyRow() {
        int[][] weights = bot.countCells(grid(1, 7), List.of(3));

        assertArrayEquals(new int[][]{{1, 2, 3, 3, 3, 2, 1}}, weights);
    }

    @Test
    @DisplayName("A miss splits the row into two separate areas")
    void missSplitsRow() {
        CellState[][] state = grid(1, 7);
        state[0][3] = CellState.MISS;

        assertArrayEquals(new int[][]{{1, 1, 1, 0, 1, 1, 1}}, bot.countCells(state, List.of(3)));
    }

    @Test
    @DisplayName("Only one position fits next to a miss on a 1x5 row")
    void missLeavesOnePosition() {
        CellState[][] state = grid(1, 5);
        state[0][1] = CellState.MISS;

        assertArrayEquals(new int[][]{{0, 0, 1, 1, 1}}, bot.countCells(state, List.of(3)));
    }

    @Test
    @DisplayName("BLOCKED cells reject positions the same way as misses")
    void blockedBehavesLikeMiss() {
        CellState[][] state = grid(1, 7);
        state[0][3] = CellState.BLOCKED;

        assertArrayEquals(new int[][]{{1, 1, 1, 0, 1, 1, 1}}, bot.countCells(state, List.of(3)));
    }

    @Test
    @DisplayName("Vertical positions are counted on a 7x1 column")
    void verticalColumn() {
        int[][] weights = bot.countCells(grid(7, 1), List.of(3));

        assertArrayEquals(new int[][]{{1}, {2}, {3}, {3}, {3}, {2}, {1}}, weights);
    }

    @Test
    @DisplayName("Both orientations are counted on a 3x3 board")
    void smallSquare() {
        int[][] weights = bot.countCells(grid(3, 3), List.of(2));

        assertArrayEquals(new int[][]{{2, 3, 2}, {3, 4, 3}, {2, 3, 2}}, weights);
    }

    @Test
    @DisplayName("A single deck is counted once, not once per orientation")
    void singleDeckCountedOnce() {
        int[][] weights = bot.countCells(grid(3, 3), List.of(1));

        assertArrayEquals(new int[][]{{1, 1, 1}, {1, 1, 1}, {1, 1, 1}}, weights);
    }

    @Test
    @DisplayName("Weights of several remaining ships are added together")
    void severalShips() {
        int[][] weights = bot.countCells(grid(1, 5), List.of(3, 1));

        assertArrayEquals(new int[][]{{2, 3, 4, 3, 2}}, weights);
    }

    @Test
    @DisplayName("Positions covering a hit get the boost")
    void hitBoost() {
        CellState[][] state = grid(1, 7);
        state[0][3] = CellState.HIT;
        int boosted = 1 + BotConstants.BOOST_VALUE;

        int[][] weights = bot.countCells(state, List.of(3));

        assertArrayEquals(new int[][]{{
                1,
                1 + boosted,
                1 + 2 * boosted,
                3 * boosted,
                1 + 2 * boosted,
                1 + boosted,
                1
        }}, weights);
    }

    private static Move move(int x, int y, MoveResult result) {
        return Move.builder().targetX(x).targetY(y).moveResult(result).build();
    }

    @Test
    @DisplayName("buildBoard returns an all-UNSHOT 10x10 board when there are no moves")
    void buildBoardEmpty() {
        CellState[][] board = bot.buildBoard(List.of());

        assertEquals(GRID_SIZE, board.length);
        for (CellState[] row : board) {
            assertEquals(GRID_SIZE, row.length);
            for (CellState cell : row) {
                assertEquals(CellState.UNSHOT, cell);
            }
        }
    }

    @Test
    @DisplayName("buildBoard puts x in the column and y in the row")
    void buildBoardCoordinates() {
        CellState[][] board = bot.buildBoard(List.of(move(3, 0, MoveResult.HIT)));

        assertEquals(CellState.HIT, board[0][3]);
        assertEquals(CellState.UNSHOT, board[3][0]);
    }

    @Test
    @DisplayName("buildBoard maps every move result to its cell state")
    void buildBoardMapsResults() {
        CellState[][] board = bot.buildBoard(List.of(
                move(0, 0, MoveResult.MISS),
                move(1, 0, MoveResult.HIT),
                move(2, 0, MoveResult.SUNK),
                move(3, 0, MoveResult.FINISHED),
                move(4, 0, MoveResult.SURVEILLANCE)));

        assertEquals(CellState.MISS, board[0][0]);
        assertEquals(CellState.HIT, board[0][1]);
        assertEquals(CellState.SUNK, board[0][2]);
        assertEquals(CellState.SUNK, board[0][3]);
        assertEquals(CellState.SURVEILLANCE, board[0][4]);
    }

    @Test
    @DisplayName("buildBoard lets the later move on a cell win")
    void buildBoardLatestWins() {
        CellState[][] board = bot.buildBoard(List.of(
                move(5, 5, MoveResult.SURVEILLANCE),
                move(5, 5, MoveResult.HIT)));

        assertEquals(CellState.HIT, board[5][5]);
    }

    private static CellState[][] rowOf(CellState... cells) {
        return new CellState[][]{cells};
    }

    private static final CellState H = CellState.HIT;
    private static final CellState S = CellState.SUNK;
    private static final CellState B = CellState.BLOCKED;
    private static final CellState U = CellState.UNSHOT;
    private static final CellState M = CellState.MISS;

    @Test
    @DisplayName("findSunk finds hits on both sides of the sunk cell")
    void findSunkBothSides() {
        CellState[][] state = rowOf(U, H, S, H, U);

        List<Integer> sizes = bot.findSunk(state);

        assertEquals(List.of(3), sizes);
        assertArrayEquals(new CellState[]{U, B, B, B, U}, state[0]);
    }

    @Test
    @DisplayName("findSunk handles a sunk cell at the last shot position of the ship end")
    void findSunkAtEnd() {
        CellState[][] state = rowOf(H, H, S);

        assertEquals(List.of(3), bot.findSunk(state));
        assertArrayEquals(new CellState[]{B, B, B}, state[0]);
    }

    @Test
    @DisplayName("findSunk handles a single deck on the board edge")
    void findSunkSingleDeckOnEdge() {
        CellState[][] state = rowOf(S, U, U);

        assertEquals(List.of(1), bot.findSunk(state));
        assertArrayEquals(new CellState[]{B, U, U}, state[0]);
    }

    @Test
    @DisplayName("findSunk handles a vertical ship touching the top and bottom edges")
    void findSunkVertical() {
        CellState[][] state = {{H}, {S}, {H}};

        assertEquals(List.of(3), bot.findSunk(state));
        assertArrayEquals(new CellState[][]{{B}, {B}, {B}}, state);
    }

    @Test
    @DisplayName("findSunk does not count hits behind a gap")
    void findSunkStopsAtGap() {
        CellState[][] state = rowOf(S, H, U, H);

        assertEquals(List.of(2), bot.findSunk(state));
        assertArrayEquals(new CellState[]{B, B, U, H}, state[0]);
    }

    @Test
    @DisplayName("findSunk leaves a diagonal hit of another ship untouched")
    void findSunkIgnoresDiagonalHit() {
        CellState[][] state = {{S, U}, {U, H}};

        assertEquals(List.of(1), bot.findSunk(state));
        assertEquals(CellState.HIT, state[1][1]);
    }

    @Test
    @DisplayName("findSunk returns one size per sunk ship")
    void findSunkTwoShips() {
        CellState[][] state = rowOf(H, S, M, S, U);

        assertEquals(List.of(2, 1), bot.findSunk(state));
        assertArrayEquals(new CellState[]{B, B, M, B, U}, state[0]);
    }

    @Test
    @DisplayName("findSunk returns nothing when no ship is sunk")
    void findSunkNone() {
        CellState[][] state = rowOf(H, U, M);

        assertEquals(List.of(), bot.findSunk(state));
        assertArrayEquals(new CellState[]{H, U, M}, state[0]);
    }

    @Test
    @DisplayName("blockDiagonal blocks the four corners around a hit and nothing else")
    void blockDiagonalMiddleHit() {
        CellState[][] state = grid(3, 3);
        state[1][1] = H;

        bot.blockDiagonal(state);

        assertArrayEquals(new CellState[][]{{B, U, B}, {U, H, U}, {B, U, B}}, state);
    }

    @Test
    @DisplayName("blockDiagonal handles a hit in the corner without leaving the board")
    void blockDiagonalCornerHit() {
        CellState[][] state = grid(3, 3);
        state[0][0] = H;

        bot.blockDiagonal(state);

        assertArrayEquals(new CellState[][]{{H, U, U}, {U, B, U}, {U, U, U}}, state);
    }

    @Test
    @DisplayName("blockDiagonal blocks the cells above and below two adjacent hits")
    void blockDiagonalAdjacentPair() {
        CellState[][] state = grid(3, 4);
        state[1][1] = H;
        state[1][2] = H;

        bot.blockDiagonal(state);

        assertArrayEquals(new CellState[][]{
                {B, B, B, B},
                {U, H, H, U},
                {B, B, B, B}}, state);
    }

    @Test
    @DisplayName("blockDiagonal keeps a diagonal miss as a miss")
    void blockDiagonalKeepsMiss() {
        CellState[][] state = grid(3, 3);
        state[1][1] = H;
        state[0][0] = M;

        bot.blockDiagonal(state);

        assertEquals(CellState.MISS, state[0][0]);
        assertEquals(CellState.BLOCKED, state[2][2]);
    }

    @Test
    @DisplayName("blockDiagonal leaves a board without hits unchanged")
    void blockDiagonalNoHits() {
        CellState[][] state = grid(3, 3);
        state[1][1] = M;

        bot.blockDiagonal(state);

        assertArrayEquals(new CellState[][]{{U, U, U}, {U, M, U}, {U, U, U}}, state);
    }

    @Test
    @DisplayName("pickCell returns the single highest-weight unshot cell")
    void pickCellSingleBest() {
        CellState[][] state = grid(1, 4);
        int[][] weights = {{1, 5, 3, 2}};

        assertEquals(new Cell(1, 0), bot.pickCell(state, weights));
    }

    @Test
    @DisplayName("pickCell never returns a hit even when it has the highest weight")
    void pickCellSkipsHit() {
        CellState[][] state = grid(1, 4);
        state[0][1] = H;
        int[][] weights = {{1, 99, 3, 2}};

        assertEquals(new Cell(2, 0), bot.pickCell(state, weights));
    }

    @Test
    @DisplayName("pickCell skips misses and blocked cells")
    void pickCellSkipsMissAndBlocked() {
        CellState[][] state = grid(1, 4);
        state[0][0] = M;
        state[0][1] = B;
        int[][] weights = {{50, 40, 1, 2}};

        assertEquals(new Cell(3, 0), bot.pickCell(state, weights));
    }

    @Test
    @DisplayName("pickCell uses x as column and y as row")
    void pickCellCoordinates() {
        CellState[][] state = grid(3, 3);
        int[][] weights = new int[3][3];
        weights[2][1] = 7;

        assertEquals(new Cell(1, 2), bot.pickCell(state, weights));
    }

    @Test
    @DisplayName("pickCell picks only among tied cells")
    void pickCellTie() {
        CellState[][] state = grid(1, 5);
        int[][] weights = {{4, 2, 4, 1, 4}};
        List<Cell> tied = List.of(new Cell(0, 0), new Cell(2, 0), new Cell(4, 0));

        for (int i = 0; i < 50; i++) {
            assertTrue(tied.contains(bot.pickCell(state, weights)));
        }
    }

    @Test
    @DisplayName("pickCell picks from unshot cells even when all their weights are zero")
    void pickCellZeroWeights() {
        CellState[][] state = grid(1, 3);
        state[0][0] = H;
        int[][] weights = {{9, 0, 0}};

        for (int i = 0; i < 20; i++) {
            Cell cell = bot.pickCell(state, weights);
            assertTrue(cell.x() == 1 || cell.x() == 2);
        }
    }

    @Test
    @DisplayName("pickCell fails when no unshot cell is left")
    void pickCellNoUnshot() {
        CellState[][] state = rowOf(H, M, B);

        assertThrows(java.util.NoSuchElementException.class, () -> bot.pickCell(state, new int[][]{{1, 1, 1}}));
    }

    @Test
    @DisplayName("remainingFleet is the full fleet when nothing is sunk")
    void remainingFleetFull() {
        List<Integer> fleet = bot.remainingFleet(List.of());

        assertEquals(10, fleet.size());
        assertEquals(List.of(1, 1, 1, 1, 2, 2, 2, 3, 3, 4), fleet.stream().sorted().toList());
    }

    @Test
    @DisplayName("remainingFleet removes one entry per sunk ship, by value")
    void remainingFleetRemovesBySize() {
        List<Integer> fleet = bot.remainingFleet(List.of(3, 1, 1));

        assertEquals(List.of(1, 1, 2, 2, 2, 3, 4), fleet.stream().sorted().toList());
    }

    private static final ToIntFunction<MoveType> NO_DRONES = type -> 0;
    private static final ToIntFunction<MoveType> TWO_DRONES = type -> 2;
    private static final ToIntFunction<MoveType> ONE_EACH = type -> 1;
    private static final ToIntFunction<MoveType> ATTACK_ONLY = type -> type == MoveType.ATTACK_DRONE ? 2 : 0;

    private static Move droneMove(MoveType type, int x, int y, boolean origin) {
        return Move.builder().targetX(x).targetY(y).moveType(type)
                .moveResult(MoveResult.MISS).isMoveOrigin(origin).build();
    }

    @Test
    @DisplayName("chooseShot without drones fires a standard shot inside the board")
    void chooseShotStandardWithoutDrones() {
        MoveRequest request = bot.chooseShot(List.of(), NO_DRONES);

        assertEquals(MoveType.STANDARD, request.moveType());
        assertTrue(request.x() >= 0 && request.x() < GRID_SIZE);
        assertTrue(request.y() >= 0 && request.y() < GRID_SIZE);
    }

    @Test
    @DisplayName("chooseShot after a lone hit fires a standard shot next to it, even with drones left")
    void chooseShotNextToHit() {
        List<Cell> neighbours = List.of(new Cell(4, 5), new Cell(6, 5), new Cell(5, 4), new Cell(5, 6));

        for (int i = 0; i < 20; i++) {
            MoveRequest request = bot.chooseShot(List.of(move(5, 5, MoveResult.HIT)), TWO_DRONES);

            assertEquals(MoveType.STANDARD, request.moveType());
            assertTrue(neighbours.contains(new Cell(request.x(), request.y())));
        }
    }

    @Test
    @DisplayName("chooseShot never fires a standard shot on a sunk ship")
    void chooseShotAvoidsSunkShip() {
        List<Move> moves = List.of(move(0, 0, MoveResult.HIT), move(1, 0, MoveResult.SUNK));

        for (int i = 0; i < 20; i++) {
            MoveRequest request = bot.chooseShot(moves, NO_DRONES);
            assertTrue(!(request.y() == 0 && (request.x() == 0 || request.x() == 1)));
        }
    }

    @Test
    @DisplayName("chooseShot hunts with an attack drone centred inside the board when only attack drones are allowed")
    void chooseShotHuntsWithAttackDrone() {
        MoveRequest request = bot.chooseShot(List.of(), ATTACK_ONLY);

        assertEquals(MoveType.ATTACK_DRONE, request.moveType());
        assertTrue(request.x() >= 1 && request.x() < GRID_SIZE - 1);
        assertTrue(request.y() >= 1 && request.y() < GRID_SIZE - 1);
    }

    @Test
    @DisplayName("chooseShot prefers the surveillance drone over the attack drone")
    void chooseShotPrefersSurveillance() {
        assertEquals(MoveType.SURVEILLANCE_DRONE, bot.chooseShot(List.of(), TWO_DRONES).moveType());
    }

    @Test
    @DisplayName("chooseShot moves on to the attack drone once the surveillance drone is used")
    void chooseShotAttackAfterSurveillance() {
        List<Move> moves = List.of(droneMove(MoveType.SURVEILLANCE_DRONE, 1, 1, true));

        assertEquals(MoveType.ATTACK_DRONE, bot.chooseShot(moves, ONE_EACH).moveType());
    }

    @Test
    @DisplayName("chooseShot stops using drones once the limit is reached")
    void chooseShotRespectsDroneLimit() {
        List<Move> moves = List.of(droneMove(MoveType.ATTACK_DRONE, 1, 1, true),
                droneMove(MoveType.ATTACK_DRONE, 8, 8, true));

        assertEquals(MoveType.STANDARD, bot.chooseShot(moves, ATTACK_ONLY).moveType());
    }

    @Test
    @DisplayName("chooseShot counts a drone use once, not once per cell it fired at")
    void chooseShotCountsOriginsOnly() {
        List<Move> moves = List.of(droneMove(MoveType.ATTACK_DRONE, 1, 1, true),
                droneMove(MoveType.ATTACK_DRONE, 0, 0, false),
                droneMove(MoveType.ATTACK_DRONE, 2, 2, false),
                droneMove(MoveType.ATTACK_DRONE, 1, 0, false));

        assertEquals(MoveType.ATTACK_DRONE, bot.chooseShot(moves, ATTACK_ONLY).moveType());
    }

    @Test
    @DisplayName("chooseShot fires a standard shot at a revealed ship cell and keeps the drones holstered")
    void chooseShotShootsRevealedCell() {
        List<Move> moves = List.of(move(5, 5, MoveResult.SURVEILLANCE));

        MoveRequest request = bot.chooseShot(moves, TWO_DRONES);

        assertEquals(new MoveRequest(5, 5, MoveType.STANDARD), request);
    }

    @Test
    @DisplayName("bestDroneCenter picks the window with the highest total weight")
    void bestDroneCenterHotspot() {
        CellState[][] state = grid(5, 5);
        int[][] weights = new int[5][5];
        weights[4][4] = 10;

        assertEquals(Optional.of(new Cell(3, 3)), bot.bestDroneCenter(state, weights));
    }

    @Test
    @DisplayName("bestDroneCenter ignores windows that contain a cell that was already shot")
    void bestDroneCenterSkipsShotCells() {
        CellState[][] state = grid(3, 3);
        state[0][0] = M;

        assertEquals(Optional.empty(), bot.bestDroneCenter(state, new int[3][3]));
    }

    @Test
    @DisplayName("bestDroneCenter finds the only window on a 3x3 board")
    void bestDroneCenterOnlyWindow() {
        assertEquals(Optional.of(new Cell(1, 1)), bot.bestDroneCenter(grid(3, 3), new int[3][3]));
    }

    @Test
    @DisplayName("pickCell shoots a revealed ship cell even when an unshot cell weighs more")
    void pickCellRevealedFirst() {
        CellState[][] state = grid(1, 4);
        state[0][0] = CellState.SURVEILLANCE;
        int[][] weights = {{1, 99, 3, 2}};

        assertEquals(new Cell(0, 0), bot.pickCell(state, weights));
    }

    @Test
    @DisplayName("A revealed ship cell boosts positions like a hit")
    void revealedCellBoost() {
        CellState[][] state = grid(1, 7);
        state[0][3] = CellState.SURVEILLANCE;
        CellState[][] hit = grid(1, 7);
        hit[0][3] = H;

        assertArrayEquals(bot.countCells(hit, List.of(3)), bot.countCells(state, List.of(3)));
    }

    @Test
    @DisplayName("blockDiagonal blocks the corners around a revealed ship cell")
    void blockDiagonalRevealedCell() {
        CellState[][] state = grid(3, 3);
        state[1][1] = CellState.SURVEILLANCE;

        bot.blockDiagonal(state);

        assertArrayEquals(new CellState[][]{{B, U, B}, {U, CellState.SURVEILLANCE, U}, {B, U, B}}, state);
    }

    private Match botMatch(MatchStatus status, boolean botOnTurn) {
        Match match = new Match();
        match.setId(7L);
        match.setInviteToken("tok");
        match.setStatus(status);
        MatchPlayer bot = new MatchPlayer();
        bot.setId(UUID.randomUUID());
        bot.setUserId(UUID.randomUUID());
        MatchPlayer human = new MatchPlayer();
        human.setId(UUID.randomUUID());
        human.setUserId(UUID.randomUUID());
        match.setPlayers(new ArrayList<>(List.of(bot, human)));
        match.setCurrentPlayerTurnId(botOnTurn ? bot.getId() : human.getId());
        when(matchService.findByInviteTokenForGame("tok")).thenReturn(Optional.of(match));
        when(userService.isBot(bot.getUserId())).thenReturn(true);
        when(droneConfig.getMaxUsagesFor(any())).thenReturn(0);
        return match;
    }

    @Test
    @DisplayName("playTurn fires the bot's chosen move through GameService")
    void playTurnFires() {
        Match match = botMatch(MatchStatus.IN_PROGRESS, true);
        MatchPlayer botPlayer = match.getPlayers().get(0);
        when(moveService.findByMatchIdAndShooterIdOrderByIdAsc(7L, botPlayer.getId())).thenReturn(List.of());

        bot.playTurn("tok");

        ArgumentCaptor<MoveRequest> captor = ArgumentCaptor.forClass(MoveRequest.class);
        verify(gameService).executeMove(eq(botPlayer), captor.capture());
        assertEquals(MoveType.STANDARD, captor.getValue().moveType());
    }

    @Test
    @DisplayName("playFallbackTurn fires a standard shot even when drones are available")
    void playFallbackTurnIsStandard() {
        Match match = botMatch(MatchStatus.IN_PROGRESS, true);
        MatchPlayer botPlayer = match.getPlayers().get(0);
        when(droneConfig.getMaxUsagesFor(any())).thenReturn(2);
        when(moveService.findByMatchIdAndShooterIdOrderByIdAsc(7L, botPlayer.getId())).thenReturn(List.of());

        bot.playFallbackTurn("tok");

        ArgumentCaptor<MoveRequest> captor = ArgumentCaptor.forClass(MoveRequest.class);
        verify(gameService).executeMove(eq(botPlayer), captor.capture());
        assertEquals(MoveType.STANDARD, captor.getValue().moveType());
    }

    @Test
    @DisplayName("createBotMatch sets up the bot's fleet and readies it")
    void createBotMatchReadiesBot() {
        UUID humanUserId = UUID.randomUUID();
        User botUser = User.builder().username(BotConstants.BOT_USERNAME).userType(UserType.BOT).build();
        botUser.setId(UUID.randomUUID());
        when(userService.findByUsername(BotConstants.BOT_USERNAME)).thenReturn(Optional.of(botUser));
        Match match = botMatch(MatchStatus.PLANNING, false);
        match.getPlayers().get(0).setUserId(botUser.getId());
        match.getPlayers().get(1).setUserId(humanUserId);
        when(matchService.createMatch(humanUserId, botUser.getId())).thenReturn(match);

        Match created = bot.createBotMatch(humanUserId);

        assertEquals(match, created);
        MatchPlayer botPlayer = match.getPlayers().get(0);
        verify(gameService).generateRandomShips(botPlayer);
        verify(gameService).markReady(botPlayer);
        verify(gameService, never()).markReady(match.getPlayers().get(1));
    }

    @Test
    @DisplayName("createBotMatch fails when the bot user is missing")
    void createBotMatchWithoutBot() {
        when(userService.findByUsername(BotConstants.BOT_USERNAME)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bot.createBotMatch(UUID.randomUUID()));
        verifyNoInteractions(gameService);
    }

    @Test
    @DisplayName("playTurn does nothing when it is the human's turn")
    void playTurnNotBotsTurn() {
        botMatch(MatchStatus.IN_PROGRESS, false);

        bot.playTurn("tok");

        verifyNoInteractions(gameService);
    }

    @Test
    @DisplayName("playTurn does nothing when the match is not in progress")
    void playTurnMatchFinished() {
        botMatch(MatchStatus.FINISHED, true);

        bot.playTurn("tok");

        verifyNoInteractions(gameService);
    }

    @Test
    @DisplayName("playTurn rejects an unknown invite token")
    void playTurnUnknownMatch() {
        when(matchService.findByInviteTokenForGame("nope")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bot.playTurn("nope"));
    }
}
