package com.chnu.seabattle.service.impl;

import com.chnu.seabattle.converter.MoveConverter;
import com.chnu.seabattle.converter.ShipConverter;
import com.chnu.seabattle.dto.move.MoveRequest;
import com.chnu.seabattle.dto.move.MoveResponse;
import com.chnu.seabattle.dto.ship.ShipRequest;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.entity.Move;
import com.chnu.seabattle.entity.MoveResult;
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
import com.chnu.seabattle.service.strategy.StandardAttackStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GameServiceImpl Tests")
class GameServiceTest {

    @Mock
    private MatchService matchService;
    @Mock
    private MatchPlayerService matchPlayerService;
    @Mock
    private MoveService moveService;
    @Mock
    private ShipRepository shipRepository;
    @Mock
    private MoveConverter moveConverter;
    @Mock
    private ShipConverter shipConverter;
    @Mock
    private WebSocketService webSocketService;
    @Mock
    private Map<MoveType, MoveStrategy> strategies;

    @InjectMocks
    private GameService gameService;

    private static final String INVITE_TOKEN = "token1";

    private Match match;
    private MatchPlayer player1;
    private MatchPlayer player2;
    private UUID playerId1;
    private UUID playerId2;

    @BeforeEach
    void setUp() {
        playerId1 = UUID.randomUUID();
        playerId2 = UUID.randomUUID();

        match = new Match();
        match.setId(1L);
        match.setInviteToken(INVITE_TOKEN);
        match.setStatus(MatchStatus.PLANNING);
        match.setCurrentPlayerTurnId(playerId1);
        match.setPlayers(new ArrayList<>());
        match.setMoves(new ArrayList<>());

        player1 = new MatchPlayer();
        player1.setId(playerId1);
        player1.setMatch(match);
        player1.setUserId(UUID.randomUUID());
        player1.setShips(new ArrayList<>());
        player1.setReady(false);
        player1.setConnected(true);

        player2 = new MatchPlayer();
        player2.setId(playerId2);
        player2.setMatch(match);
        player2.setUserId(UUID.randomUUID());
        player2.setShips(new ArrayList<>());
        player2.setReady(false);
        player2.setConnected(true);

        match.getPlayers().add(player1);
        match.getPlayers().add(player2);
    }

    @Nested
    @DisplayName("placeShip Tests")
    class PlaceShipTests {

        @Test
        @DisplayName("Should successfully place a ship")
        void shouldSuccessfullyPlaceShip() {
            when(matchPlayerService.findById(playerId1)).thenReturn(Optional.of(player1));
            when(matchService.findByInviteTokenForGame(INVITE_TOKEN)).thenReturn(Optional.of(match));
            when(shipRepository.save(any())).thenReturn(Ship.builder()
                    .id(1L)
                    .matchPlayer(player1)
                    .shipType(ShipType.QUADRO_DECK)
                    .startX(0).startY(0)
                    .hits(0).isSunk(false)
                    .orientation(Orientation.HORIZONTAL)
                    .build());

            when(shipConverter.toEntity(any(ShipRequest.class))).thenReturn(Ship.builder().build());

            Ship placedShip = gameService.placeShip(INVITE_TOKEN, playerId1, new ShipRequest(ShipType.QUADRO_DECK, 0, 0, Orientation.HORIZONTAL));

            assertEquals(1, player1.getShips().size());
            assertEquals(ShipType.QUADRO_DECK, placedShip.getShipType());
            assertEquals(0, placedShip.getStartX());
            assertEquals(0, placedShip.getStartY());
            assertEquals(Orientation.HORIZONTAL, placedShip.getOrientation());
            assertEquals(0, placedShip.getHits());
            assertFalse(placedShip.isSunk());
        }

        @Test
        @DisplayName("Should throw exception when player not found")
        void shouldThrowExceptionWhenPlayerNotFound() {
            when(matchPlayerService.findById(any(UUID.class))).thenReturn(Optional.empty());
            ShipRequest shipRequest = new ShipRequest(ShipType.SINGLE_DECK, 0, 0, Orientation.HORIZONTAL);

            assertThrows(ResourceNotFoundException.class, () ->
                    gameService.placeShip(INVITE_TOKEN, playerId1, shipRequest)
            );
        }

        @Test
        @DisplayName("Should throw exception when match not found")
        void shouldThrowExceptionWhenMatchNotFound() {
            when(matchPlayerService.findById(playerId1)).thenReturn(Optional.of(player1));
            when(matchService.findByInviteTokenForGame(anyString())).thenReturn(Optional.empty());
            ShipRequest shipRequest = new ShipRequest(ShipType.SINGLE_DECK, 0, 0, Orientation.HORIZONTAL);


            assertThrows(ResourceNotFoundException.class, () ->
                    gameService.placeShip(INVITE_TOKEN, playerId1, shipRequest)
            );
        }

        @Test
        @DisplayName("Should throw exception when player is already ready")
        void shouldThrowExceptionWhenPlayerAlreadyReady() {
            player1.setReady(true);
            when(matchPlayerService.findById(playerId1)).thenReturn(Optional.of(player1));
            when(matchService.findByInviteTokenForGame(INVITE_TOKEN)).thenReturn(Optional.of(match));
            ShipRequest shipRequest = new ShipRequest(ShipType.SINGLE_DECK, 0, 0, Orientation.HORIZONTAL);

            GameRuleViolationException exception = assertThrows(GameRuleViolationException.class, () ->
                    gameService.placeShip(INVITE_TOKEN, playerId1, shipRequest)
            );

            assertEquals("Action unsupported: player is already ready", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception when ship type limit is exceeded")
        void shouldThrowExceptionWhenShipTypeLimitExceeded() {
            player1.getShips().add(Ship.builder()
                    .matchPlayer(player1).shipType(ShipType.QUADRO_DECK)
                    .startX(0).startY(0).orientation(Orientation.HORIZONTAL)
                    .hits(0).isSunk(false).build());

            when(matchPlayerService.findById(playerId1)).thenReturn(Optional.of(player1));
            when(matchService.findByInviteTokenForGame(INVITE_TOKEN)).thenReturn(Optional.of(match));
            ShipRequest shipRequest = new ShipRequest(ShipType.QUADRO_DECK, 0, 5, Orientation.HORIZONTAL);

            GameRuleViolationException exception = assertThrows(GameRuleViolationException.class, () ->
                    gameService.placeShip(INVITE_TOKEN, playerId1, shipRequest)
            );

            assertTrue(exception.getMessage().contains("Ship limit reached for type"));
        }
    }

    @Nested
    @DisplayName("markReady Tests")
    class MarkReadyTests {

        @Test
        @DisplayName("Should successfully mark player as ready")
        void shouldSuccessfullyMarkPlayerAsReady() {
            when(matchService.getByInviteToken(INVITE_TOKEN)).thenReturn(match);

            gameService.markReady(INVITE_TOKEN, player1);

            assertTrue(player1.isReady());
        }

        @Test
        @DisplayName("Should throw exception when match not found")
        void shouldThrowExceptionWhenMatchNotFound() {
            when(matchService.getByInviteToken(anyString())).thenThrow(ResourceNotFoundException.class);

            assertThrows(ResourceNotFoundException.class, () ->
                    gameService.markReady(INVITE_TOKEN, player1)
            );
        }

        @Test
        @DisplayName("Should throw exception when match is not in PLANNING status")
        void shouldThrowExceptionWhenNotInPlanningStatus() {
            match.setStatus(MatchStatus.IN_PROGRESS);
            when(matchService.getByInviteToken(INVITE_TOKEN)).thenReturn(match);

            GameRuleViolationException exception = assertThrows(GameRuleViolationException.class, () ->
                    gameService.markReady(INVITE_TOKEN, player1)
            );

            assertTrue(exception.getMessage().contains("Action not allowed in match state"));
        }

        @Test
        @DisplayName("Should throw exception when player is already ready")
        void shouldThrowWhenPlayerAlreadyReady() {
            player1.setReady(true);
            when(matchService.getByInviteToken(INVITE_TOKEN)).thenReturn(match);

            assertThrows(GameRuleViolationException.class, () ->
                    gameService.markReady(INVITE_TOKEN, player1)
            );
        }

        @Test
        @DisplayName("Match stays PLANNING when only first player is ready")
        void matchStaysPlanningWhenOnlyOnePlayerReady() {
            when(matchService.getByInviteToken(INVITE_TOKEN)).thenReturn(match);

            gameService.markReady(INVITE_TOKEN, player1);

            assertEquals(MatchStatus.PLANNING, match.getStatus());
        }

        @Test
        @DisplayName("Match transitions to IN_PROGRESS when both players are ready")
        void matchTransitionsToInProgressWhenBothPlayersReady() {
            player2.setReady(true);
            when(matchService.getByInviteToken(INVITE_TOKEN)).thenReturn(match);

            gameService.markReady(INVITE_TOKEN, player1);

            assertEquals(MatchStatus.IN_PROGRESS, match.getStatus());
            assertTrue(
                    match.getCurrentPlayerTurnId().equals(playerId1) ||
                            match.getCurrentPlayerTurnId().equals(playerId2)
            );
        }
    }

    @Nested
    @DisplayName("fire Tests")
    class FireTests {

        @BeforeEach
        void setUpFire() {
            match.setStatus(MatchStatus.IN_PROGRESS);
            match.setCurrentPlayerTurnId(playerId1);

            Ship ship = Ship.builder()
                    .id(1L)
                    .matchPlayer(player2)
                    .shipType(ShipType.TRIPLE_DECK)
                    .startX(5).startY(5)
                    .orientation(Orientation.HORIZONTAL)
                    .hits(0).isSunk(false)
                    .build();
            player2.getShips().add(ship);

            StandardAttackStrategy standardStrategy = new StandardAttackStrategy(moveService, moveConverter);
            lenient().when(strategies.get(MoveType.STANDARD)).thenReturn(standardStrategy);
            lenient().when(moveService.create(any(Move.class))).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(moveConverter.toResponse(any(Move.class))).thenAnswer(inv -> {
                Move m = inv.getArgument(0);
                return new MoveResponse(m.getTargetX(), m.getTargetY(), m.getMoveResult());
            });
        }

        @Test
        @DisplayName("Should successfully fire and hit a ship")
        void shouldSuccessfullyFireAndHitShip() {
            when(matchService.findByInviteTokenForGame(INVITE_TOKEN)).thenReturn(Optional.of(match));

            List<Move> result = gameService.executeMove(INVITE_TOKEN, playerId1, new MoveRequest(5, 5, MoveType.STANDARD));

            assertEquals(MoveResult.HIT, result.getFirst().getMoveResult());
            verify(moveService).create(any(Move.class));
            assertEquals(1, player2.getShips().getFirst().getHits());
            assertFalse(player2.getShips().getFirst().isSunk());
        }

        @Test
        @DisplayName("Should successfully fire and miss")
        void shouldSuccessfullyFireAndMiss() {
            when(matchService.findByInviteTokenForGame(INVITE_TOKEN)).thenReturn(Optional.of(match));

            List<Move> result = gameService.executeMove(INVITE_TOKEN, playerId1, new MoveRequest(0, 0, MoveType.STANDARD));

            assertEquals(MoveResult.MISS, result.getFirst().getMoveResult());
            verify(moveService).create(any(Move.class));
            assertEquals(player2.getId(), match.getCurrentPlayerTurnId());
        }

        @Test
        @DisplayName("Should successfully fire and sink all ships")
        void shouldSuccessfullyFireAndSinkShip() {
            player2.getShips().getFirst().setHits(2);

            when(matchService.findByInviteTokenForGame(INVITE_TOKEN)).thenReturn(Optional.of(match));

            List<Move> result = gameService.executeMove(INVITE_TOKEN, playerId1, new MoveRequest(5, 5, MoveType.STANDARD));

            assertEquals(MoveResult.FINISHED, result.getFirst().getMoveResult());
            verify(moveService, atLeastOnce()).create(any(Move.class));
            assertEquals(3, player2.getShips().getFirst().getHits());
            assertTrue(player2.getShips().getFirst().isSunk());
        }

        @Test
        @DisplayName("Should end game when all ships are sunk")
        void shouldEndGameWhenAllShipsSunk() {
            player2.getShips().getFirst().setHits(2);

            when(matchService.findByInviteTokenForGame(INVITE_TOKEN)).thenReturn(Optional.of(match));

            gameService.executeMove(INVITE_TOKEN, playerId1, new MoveRequest(5, 5, MoveType.STANDARD));

            assertEquals(MatchStatus.FINISHED, match.getStatus());
            assertEquals(playerId1, match.getWinnerId());
            assertNotNull(match.getFinishedAt());
        }

        @Test
        @DisplayName("Turn stays with shooter after a hit")
        void turnStaysWithShooterAfterHit() {
            when(matchService.findByInviteTokenForGame(INVITE_TOKEN)).thenReturn(Optional.of(match));

            gameService.executeMove(INVITE_TOKEN, playerId1, new MoveRequest(5, 5, MoveType.STANDARD));

            assertEquals(playerId1, match.getCurrentPlayerTurnId());
        }

        @Test
        @DisplayName("Should return SUNK when ship is sunk but game continues")
        void shouldReturnSunkWhenShipSunkButGameContinues() {
            player2.getShips().getFirst().setHits(2);
            player2.getShips().add(Ship.builder()
                    .id(2L).matchPlayer(player2).shipType(ShipType.SINGLE_DECK)
                    .startX(0).startY(0).orientation(Orientation.HORIZONTAL)
                    .hits(0).isSunk(false).build());

            when(matchService.findByInviteTokenForGame(INVITE_TOKEN)).thenReturn(Optional.of(match));

            List<Move> result = gameService.executeMove(INVITE_TOKEN, playerId1, new MoveRequest(5, 5, MoveType.STANDARD));

            assertTrue(result.stream().anyMatch(m -> MoveResult.SUNK.equals(m.getMoveResult())));
            assertEquals(MatchStatus.IN_PROGRESS, match.getStatus());
        }

        @Test
        @DisplayName("Should throw exception when match not found")
        void shouldThrowExceptionWhenMatchNotFound() {
            when(matchService.findByInviteTokenForGame(anyString())).thenReturn(Optional.empty());
            MoveRequest moveRequest = new MoveRequest(5, 5, MoveType.STANDARD);


            assertThrows(ResourceNotFoundException.class, () ->
                    gameService.executeMove(INVITE_TOKEN, playerId1, moveRequest)
            );
        }

        @Test
        @DisplayName("Should throw exception when not player's turn")
        void shouldThrowExceptionWhenNotPlayersTurn() {
            match.setCurrentPlayerTurnId(playerId2);
            when(matchService.findByInviteTokenForGame(INVITE_TOKEN)).thenReturn(Optional.of(match));
            MoveRequest moveRequest = new MoveRequest(5, 5, MoveType.STANDARD);

            GameRuleViolationException exception = assertThrows(GameRuleViolationException.class, () ->
                    gameService.executeMove(INVITE_TOKEN, playerId1, moveRequest)
            );

            assertEquals("It's not the player's turn", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception when match is not in IN_PROGRESS status")
        void shouldThrowExceptionWhenNotInProgressStatus() {
            match.setStatus(MatchStatus.PLANNING);
            when(matchService.findByInviteTokenForGame(INVITE_TOKEN)).thenReturn(Optional.of(match));
            MoveRequest moveRequest = new MoveRequest(5, 5, MoveType.STANDARD);

            GameRuleViolationException exception = assertThrows(GameRuleViolationException.class, () ->
                    gameService.executeMove(INVITE_TOKEN, playerId1, moveRequest)
            );

            assertTrue(exception.getMessage().contains("Action not allowed in match state"));
        }

        @Test
        @DisplayName("Should throw exception when firing coordinates are out of bounds")
        void shouldThrowExceptionWhenFiringOutOfBounds() {
            when(matchService.findByInviteTokenForGame(INVITE_TOKEN)).thenReturn(Optional.of(match));
            MoveRequest moveRequest = new MoveRequest(10, 10, MoveType.STANDARD);

            GameRuleViolationException exception = assertThrows(GameRuleViolationException.class, () ->
                    gameService.executeMove(INVITE_TOKEN, playerId1, moveRequest)
            );

            assertEquals("Coordinates out of bounds", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception when firing at same coordinates twice")
        void shouldThrowExceptionWhenFiringAtSameCoordinatesTwice() {
            when(matchService.findByInviteTokenForGame(INVITE_TOKEN)).thenReturn(Optional.of(match));
            when(moveService.existsByMatchIdAndShooterIdAndTargetXAndTargetYAndMoveType(1L, playerId1, 5, 5, MoveType.STANDARD)).thenReturn(true);
            MoveRequest moveRequest = new MoveRequest(5, 5, MoveType.STANDARD);
            GameRuleViolationException exception = assertThrows(GameRuleViolationException.class, () ->
                    gameService.executeMove(INVITE_TOKEN, playerId1, moveRequest)
            );

            assertEquals("Cannot fire at the same coordinates twice", exception.getMessage());
        }

        @Test
        @DisplayName("Should handle vertical ship hit correctly")
        void shouldHandleVerticalShipHitCorrectly() {
            player2.getShips().clear();
            player2.getShips().add(Ship.builder()
                    .id(2L).matchPlayer(player2).shipType(ShipType.DOUBLE_DECK)
                    .startX(3).startY(3).orientation(Orientation.VERTICAL)
                    .hits(0).isSunk(false).build());

            when(matchService.findByInviteTokenForGame(INVITE_TOKEN)).thenReturn(Optional.of(match));

            List<Move> result = gameService.executeMove(INVITE_TOKEN, playerId1, new MoveRequest(3, 4, MoveType.STANDARD));

            assertEquals(MoveResult.HIT, result.getFirst().getMoveResult());
            assertEquals(1, player2.getShips().getFirst().getHits());
        }
    }

    @Nested
    @DisplayName("moveShip Tests")
    class MoveShipTests {

        @BeforeEach
        void setUpMoveShip() {
            player1.getShips().add(Ship.builder()
                    .id(10L).matchPlayer(player1).shipType(ShipType.DOUBLE_DECK)
                    .startX(0).startY(0).orientation(Orientation.HORIZONTAL)
                    .hits(0).isSunk(false).build());
            lenient().when(matchPlayerService.findById(playerId1)).thenReturn(Optional.of(player1));
            lenient().when(matchService.findByInviteTokenForGame(INVITE_TOKEN)).thenReturn(Optional.of(match));
        }

        @Test
        @DisplayName("Should successfully move a ship to a new position")
        void shouldSuccessfullyMoveShip() {
            Ship result = gameService.moveShip(INVITE_TOKEN, playerId1, 10L, 5, 5, Orientation.VERTICAL);

            assertEquals(5, result.getStartX());
            assertEquals(5, result.getStartY());
            assertEquals(Orientation.VERTICAL, result.getOrientation());
        }

        @Test
        @DisplayName("Should throw exception when match not found")
        void shouldThrowWhenMatchNotFound() {
            when(matchService.findByInviteTokenForGame(anyString())).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                    gameService.moveShip(INVITE_TOKEN, playerId1, 10L, 5, 5, Orientation.VERTICAL)
            );
        }

        @Test
        @DisplayName("Should throw exception when player not in match")
        void shouldThrowWhenPlayerNotInMatch() {
            when(matchPlayerService.findById(playerId1)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                    gameService.moveShip(INVITE_TOKEN, playerId1, 10L, 5, 5, Orientation.VERTICAL)
            );
        }

        @Test
        @DisplayName("Should throw exception when player is already ready")
        void shouldThrowWhenPlayerAlreadyReady() {
            player1.setReady(true);
            when(matchService.findByInviteTokenForGame(INVITE_TOKEN)).thenReturn(Optional.of(match));

            assertThrows(GameRuleViolationException.class, () ->
                    gameService.moveShip(INVITE_TOKEN, playerId1, 10L, 5, 5, Orientation.VERTICAL)
            );
        }

        @Test
        @DisplayName("Should throw exception when ship not found")
        void shouldThrowWhenShipNotFound() {
            when(matchService.findByInviteTokenForGame(INVITE_TOKEN)).thenReturn(Optional.of(match));

            assertThrows(ResourceNotFoundException.class, () ->
                    gameService.moveShip(INVITE_TOKEN, playerId1, 999L, 5, 5, Orientation.VERTICAL)
            );
        }

        @Test
        @DisplayName("Should throw exception when new placement is invalid")
        void shouldThrowWhenNewPlacementIsInvalid() {
            player1.getShips().add(Ship.builder()
                    .id(11L).matchPlayer(player1).shipType(ShipType.SINGLE_DECK)
                    .startX(5).startY(5).orientation(Orientation.HORIZONTAL)
                    .hits(0).isSunk(false).build());
            when(matchService.findByInviteTokenForGame(INVITE_TOKEN)).thenReturn(Optional.of(match));

            assertThrows(GameRuleViolationException.class, () ->
                    gameService.moveShip(INVITE_TOKEN, playerId1, 10L, 5, 5, Orientation.HORIZONTAL)
            );
        }
    }

    @Nested
    @DisplayName("handleDisconnect Tests")
    class HandleDisconnectTests {

        @Test
        @DisplayName("Should successfully handle player disconnect")
        void shouldSuccessfullyHandleDisconnect() {
            when(matchService.findByInviteTokenForGame(match.getInviteToken())).thenReturn(Optional.of(match));
            gameService.handleDisconnect(match.getInviteToken(), player1.getId());

            assertFalse(player1.isConnected());
            assertNotNull(player1.getLastSeenAt());
        }
    }

    @Nested
    @DisplayName("handleReconnect Tests")
    class HandleReconnectTests {

        @Test
        @DisplayName("Should successfully handle player reconnect")
        void shouldSuccessfullyHandleReconnect() {
            player1.setConnected(false);

            gameService.handleReconnect(match, player1);

            assertTrue(player1.isConnected());
            assertNotNull(player1.getLastSeenAt());
        }
    }
}
