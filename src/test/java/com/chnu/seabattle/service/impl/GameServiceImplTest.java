package com.chnu.seabattle.service.impl;

import com.chnu.seabattle.converter.MoveConverter;
import com.chnu.seabattle.converter.ShipConverter;
import com.chnu.seabattle.dto.FireResult;
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
import com.chnu.seabattle.service.MatchPlayerService;
import com.chnu.seabattle.service.MatchService;
import com.chnu.seabattle.service.MoveService;
import com.chnu.seabattle.service.WebSocketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GameServiceImpl Tests")
class GameServiceImplTest {

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

    @InjectMocks
    private GameServiceImpl gameService;

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

    // ==================== placeShip Tests ====================

    @Nested
    @DisplayName("placeShip Tests")
    class PlaceShipTests {

        @Test
        @DisplayName("Should successfully place a ship")
        void shouldSuccessfullyPlaceShip() {
            when(matchPlayerService.findById(playerId1)).thenReturn(Optional.of(player1));
            when(matchService.findByIdForGame(1L)).thenReturn(Optional.of(match));
            when(shipRepository.save(any())).thenReturn(Ship.builder()
                    .id(1L)
                    .matchPlayer(player1)
                    .shipType(ShipType.QUADRO_DECK)
                    .startX(0)
                    .startY(0)
                    .hits(0)
                    .isSunk(false)
                    .orientation(Orientation.HORIZONTAL)
                    .build());

            Ship placedShip = gameService.placeShip(1L, playerId1, ShipType.QUADRO_DECK, 0, 0, Orientation.HORIZONTAL);

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

            assertThrows(ResourceNotFoundException.class, () ->
                    gameService.placeShip(1L, playerId1, ShipType.SINGLE_DECK, 0, 0, Orientation.HORIZONTAL)
            );
        }


//        @Test
//        @DisplayName("Should throw exception when match is not in PLANNING status")
//        void shouldThrowExceptionWhenNotInPlanningStatus() {
//            match.setStatus(MatchStatus.IN_PROGRESS);
//            when(matchRepository.findById(1L)).thenReturn(Optional.of(match));
//
//            GameRuleViolationException exception = assertThrows(GameRuleViolationException.class, () ->
//                    gameService.placeShip(1L, playerId1, ShipType.SINGLE_DECK, 0, 0, Orientation.HORIZONTAL)
//            );
//
//            assertTrue(exception.getMessage().contains("Action not allowed in state"));
//        }

        @Test
        @DisplayName("Should throw exception when player is already ready")
        void shouldThrowExceptionWhenPlayerAlreadyReady() {
            player1.setReady(true);
            when(matchPlayerService.findById(playerId1)).thenReturn(Optional.of(player1));
            when(matchService.findByIdForGame(1L)).thenReturn(Optional.of(match));

            GameRuleViolationException exception = assertThrows(GameRuleViolationException.class, () ->
                    gameService.placeShip(1L, playerId1, ShipType.SINGLE_DECK, 0, 0, Orientation.HORIZONTAL)
            );

            assertEquals("Action unsupported: player is already ready", exception.getMessage());
        }

        // ==================== markReady Tests ====================

        @Nested
        @DisplayName("markReady Tests")
        class MarkReadyTests {

            @Test
            @DisplayName("Should successfully mark player as ready")
            void shouldSuccessfullyMarkPlayerAsReady() {
                when(matchService.findById(1L)).thenReturn(Optional.of(match));

                gameService.markReady(1L, playerId1);

                assertTrue(player1.isReady());
            }

            @Test
            @DisplayName("Should throw exception when match not found")
            void shouldThrowExceptionWhenMatchNotFound() {
                when(matchService.findById(anyLong())).thenReturn(Optional.empty());

                assertThrows(ResourceNotFoundException.class, () ->
                        gameService.markReady(1L, playerId1)
                );
            }

            @Test
            @DisplayName("Should throw exception when match is not in PLANNING status")
            void shouldThrowExceptionWhenNotInPlanningStatus() {
                match.setStatus(MatchStatus.IN_PROGRESS);
                when(matchService.findById(1L)).thenReturn(Optional.of(match));

                GameRuleViolationException exception = assertThrows(GameRuleViolationException.class, () ->
                        gameService.markReady(1L, playerId1)
                );

                assertTrue(exception.getMessage().contains("Action not allowed in match state"));
            }
        }

        // ==================== fire Tests ====================

        @Nested
        @DisplayName("fire Tests")
        class FireTests {

            @BeforeEach
            void setUpFire() {
                match.setStatus(MatchStatus.IN_PROGRESS);
                match.setCurrentPlayerTurnId(playerId1);

                // Add a ship to player2's board
                Ship ship = Ship.builder()
                        .id(1L)
                        .matchPlayer(player2)
                        .shipType(ShipType.TRIPLE_DECK)
                        .startX(5)
                        .startY(5)
                        .orientation(Orientation.HORIZONTAL)
                        .hits(0)
                        .isSunk(false)
                        .build();
                player2.getShips().add(ship);

            }

            @Test
            @DisplayName("Should successfully fire and hit a ship")
            void shouldSuccessfullyFireAndHitShip() {
                when(matchService.findByIdForGame(1L)).thenReturn(Optional.of(match));

                FireResult result = gameService.fire(1L, playerId1, 5, 5);

                assertEquals(MoveResult.HIT, result.moveResult());
                verify(moveService).create(any(Move.class));
                assertEquals(1, player2.getShips().getFirst().getHits());
                assertFalse(player2.getShips().getFirst().isSunk());
            }

            @Test
            @DisplayName("Should successfully fire and miss")
            void shouldSuccessfullyFireAndMiss() {
                when(matchService.findByIdForGame(1L)).thenReturn(Optional.of(match));

                FireResult result = gameService.fire(1L, playerId1, 0, 0);

                assertEquals(MoveResult.MISS, result.moveResult());
                verify(moveService).create(any(Move.class));
                assertEquals(player2.getId(), match.getCurrentPlayerTurnId());
            }

            @Test
            @DisplayName("Should successfully fire and sink a ship")
            void shouldSuccessfullyFireAndSinkShip() {
                player2.getShips().getFirst().setHits(2); // Already hit twice

                when(matchService.findByIdForGame(1L)).thenReturn(Optional.of(match));

                FireResult result = gameService.fire(1L, playerId1, 5, 5);

                assertEquals(MoveResult.FINISHED, result.moveResult());
                verify(moveService).create(any(Move.class));
                assertEquals(3, player2.getShips().getFirst().getHits());
                assertTrue(player2.getShips().getFirst().isSunk());
            }

            @Test
            @DisplayName("Should end game when all ships are sunk")
            void shouldEndGameWhenAllShipsSunk() {
                player2.getShips().getFirst().setHits(2); // Already hit twice

                when(matchService.findByIdForGame(1L)).thenReturn(Optional.of(match));

                FireResult result = gameService.fire(1L, playerId1, 5, 5);

                assertEquals(MoveResult.FINISHED, result.moveResult());
                assertEquals(MatchStatus.FINISHED, match.getStatus());
                assertEquals(playerId1, match.getWinnerId());
                assertNotNull(match.getFinishedAt());
            }

            @Test
            @DisplayName("Should throw exception when match not found")
            void shouldThrowExceptionWhenMatchNotFound() {
                when(matchService.findByIdForGame(anyLong())).thenReturn(Optional.empty());

                assertThrows(ResourceNotFoundException.class, () ->
                        gameService.fire(1L, playerId1, 5, 5)
                );
            }

            @Test
            @DisplayName("Should throw exception when not player's turn")
            void shouldThrowExceptionWhenNotPlayersTurn() {
                match.setCurrentPlayerTurnId(playerId2);
                when(matchService.findByIdForGame(1L)).thenReturn(Optional.of(match));

                GameRuleViolationException exception = assertThrows(GameRuleViolationException.class, () ->
                        gameService.fire(1L, playerId1, 5, 5)
                );

                assertEquals("It's not the player's turn", exception.getMessage());
            }

            @Test
            @DisplayName("Should throw exception when match is not in IN_PROGRESS status")
            void shouldThrowExceptionWhenNotInProgressStatus() {
                match.setStatus(MatchStatus.PLANNING);
                when(matchService.findByIdForGame(1L)).thenReturn(Optional.of(match));

                GameRuleViolationException exception = assertThrows(GameRuleViolationException.class, () ->
                        gameService.fire(1L, playerId1, 5, 5)
                );

                assertTrue(exception.getMessage().contains("Action not allowed in match state"));
            }

            @Test
            @DisplayName("Should throw exception when firing coordinates are out of bounds")
            void shouldThrowExceptionWhenFiringOutOfBounds() {
                when(matchService.findByIdForGame(1L)).thenReturn(Optional.of(match));

                GameRuleViolationException exception = assertThrows(GameRuleViolationException.class, () ->
                        gameService.fire(1L, playerId1, 10, 10)
                );

                assertEquals("Coordinates out of bounds", exception.getMessage());
            }

            @Test
            @DisplayName("Should throw exception when firing at same coordinates twice")
            void shouldThrowExceptionWhenFiringAtSameCoordinatesTwice() {
                when(matchService.findByIdForGame(1L)).thenReturn(Optional.of(match));
                when(moveService.existsByMatchIdAndShooterIdAndTargetXAndTargetY(1L, playerId1, 5, 5)).thenReturn(true);

                GameRuleViolationException exception = assertThrows(GameRuleViolationException.class, () ->
                        gameService.fire(1L, playerId1, 5, 5)
                );

                assertEquals("Cannot fire at the same coordinates twice", exception.getMessage());
            }

            @Test
            @DisplayName("Should handle vertical ship hit correctly")
            void shouldHandleVerticalShipHitCorrectly() {
                player2.getShips().clear();
                Ship verticalShip = Ship.builder()
                        .id(2L)
                        .matchPlayer(player2)
                        .shipType(ShipType.DOUBLE_DECK)
                        .startX(3)
                        .startY(3)
                        .orientation(Orientation.VERTICAL)
                        .hits(0)
                        .isSunk(false)
                        .build();
                player2.getShips().add(verticalShip);

                when(matchService.findByIdForGame(1L)).thenReturn(Optional.of(match));

                FireResult result = gameService.fire(1L, playerId1, 3, 4);

                assertEquals(MoveResult.HIT, result.moveResult());
                assertEquals(1, player2.getShips().getFirst().getHits());
            }
        }

        // ==================== handleDisconnect Tests ====================

        @Nested
        @DisplayName("handleDisconnect Tests")
        class HandleDisconnectTests {

            @Test
            @DisplayName("Should successfully handle player disconnect")
            void shouldSuccessfullyHandleDisconnect() {
                gameService.handleDisconnect(match, player1);

                assertFalse(player1.isConnected());
                assertNotNull(player1.getLastSeenAt());
            }
        }

        // ==================== handleReconnect Tests ====================

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

        // ==================== Guest Player Tests ====================

        @Nested
        @DisplayName("Guest Player Tests")
        class GuestPlayerTests {

//            @Test
//            @DisplayName("Should successfully place ship for guest player")
//            void shouldSuccessfullyPlaceShipForGuest() {
//                UUID guestId = UUID.randomUUID();
//                player1.setUserId(null);
//                player1.setGuestId(guestId);
//
//                when(matchRepository.findById(1L)).thenReturn(Optional.of(match));
//                when(matchPlayerRepository.findByMatchIdAndUserIdOrMatchIdAndGuestId(
//                        anyLong(), any(UUID.class), anyLong(), any(UUID.class)))
//                        .thenReturn(Optional.of(player1));
//
//                gameService.placeShip(1L, guestId, ShipType.SINGLE_DECK, 0, 0, Orientation.HORIZONTAL);
//
//                verify(matchPlayerRepository).save(player1);
//                assertEquals(1, player1.getShips().size());
//            }

//            @Test
//            @DisplayName("Should switch turn to guest player after miss")
//            void shouldSwitchTurnToGuestPlayerAfterMiss() {
//                match.setStatus(MatchStatus.IN_PROGRESS);
//                UUID guestId = UUID.randomUUID();
//                player2.setUserId(null);
//                player2.setGuestId(guestId);
//
//                when(matchRepository.findById(1L)).thenReturn(Optional.of(match));
//                when(matchPlayerRepository.findByMatchIdAndUserIdOrMatchIdAndGuestId(
//                        anyLong(), any(UUID.class), anyLong(), any(UUID.class)))
//                        .thenReturn(Optional.of(player1));
//
//                MoveResult result = gameService.fire(1L, playerId1, 0, 0);
//
//                assertEquals(MoveResult.MISS, result);
//                assertEquals(guestId, match.getCurrentPlayerTurnId());
//            }
        }
    }
}
