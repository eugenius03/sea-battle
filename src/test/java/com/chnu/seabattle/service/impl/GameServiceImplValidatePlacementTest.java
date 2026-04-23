package com.chnu.seabattle.service.impl;

import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.entity.Orientation;
import com.chnu.seabattle.entity.Ship;
import com.chnu.seabattle.entity.ShipType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
@DisplayName("GameServiceImpl - validatePlacement Tests")
class GameServiceImplValidatePlacementTest {

    @InjectMocks
    private GameServiceImpl gameService;

    private Method isValidPlacementMethod;
    private Method isValidCoordinatesMethod;

    @BeforeEach
    void setUp() throws NoSuchMethodException {

        // Use reflection to access the private methods
        isValidPlacementMethod = GameServiceImpl.class.getDeclaredMethod(
                "isValidPlacement",
                java.util.List.class,
                ShipType.class,
                int.class,
                int.class,
                Orientation.class
        );
        isValidPlacementMethod.setAccessible(true);

        isValidCoordinatesMethod = GameServiceImpl.class.getDeclaredMethod(
                "isValidCoordinates",
                int.class,
                int.class,
                ShipType.class,
                Orientation.class
        );
        isValidCoordinatesMethod.setAccessible(true);
    }

    private boolean invokeIsValidPlacement(
            java.util.List<Ship> ships,
            ShipType type,
            int x,
            int y,
            Orientation orientation
    ) throws Throwable {
        try {
            return (boolean) isValidPlacementMethod.invoke(gameService, ships, type, x, y, orientation);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private boolean invokeIsValidCoordinates(
            int x,
            int y,
            ShipType shipType,
            Orientation orientation
    ) throws Throwable {
        try {
            return (boolean) isValidCoordinatesMethod.invoke(gameService, x, y, shipType, orientation);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private MatchPlayer createMatchPlayer(UUID playerId) {
        Match match = new Match();
        match.setId(1L);
        match.setStatus(MatchStatus.PLANNING);
        match.setCurrentPlayerTurnId(playerId);

        MatchPlayer player = new MatchPlayer();
        player.setId(UUID.randomUUID());
        player.setMatch(match);

        player.setUserId(playerId);
//        if (isGuest) {
//            player.setGuestId(playerId);
//        } else {
//        }

        player.setShips(new ArrayList<>());
        player.setReady(false);

        return player;
    }

    private Ship createShip(MatchPlayer player, ShipType type, int startX, int startY, Orientation orientation) {
        Ship ship = new Ship();
        ship.setMatchPlayer(player);
        ship.setShipType(type);
        ship.setStartX(startX);
        ship.setStartY(startY);
        ship.setOrientation(orientation);
        ship.setHits(0);
        ship.setSunk(false);
        return ship;
    }

    @Test
    @DisplayName("Should pass validation when placing first ship")
    void shouldPassValidationForFirstShip() throws Throwable {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        boolean result = invokeIsValidPlacement(player.getShips(), ShipType.QUADRO_DECK, 0, 0, Orientation.HORIZONTAL);
        assertEquals(true, result);
    }

//    @Test
//    @DisplayName("Should pass validation for guest player")
//    void shouldPassValidationForGuestPlayer() {
//        UUID guestId = UUID.randomUUID();
//        MatchPlayer player = createMatchPlayer(guestId, true);
//
//        assertDoesNotThrow(() ->
//                invokeValidatePlacement(player, ShipType.SINGLE_DECK, 5, 5, Orientation.HORIZONTAL)
//        );
//    }

    @Test
    @DisplayName("Should throw exception when ship overlaps another ship - horizontal overlap")
    void shouldThrowExceptionWhenShipOverlapsHorizontal() throws Throwable {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        // Place a horizontal ship at (0,0)
        player.getShips().add(createShip(player, ShipType.TRIPLE_DECK, 0, 0, Orientation.HORIZONTAL));

        // Try to place another ship overlapping at (1,0)
        boolean result = invokeIsValidPlacement(player.getShips(), ShipType.DOUBLE_DECK, 1, 0, Orientation.HORIZONTAL);

        assertEquals(false, result);
    }

    @Test
    @DisplayName("Should throw exception when ship overlaps another ship - vertical overlap")
    void shouldThrowExceptionWhenShipOverlapsVertical() throws Throwable {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        // Place a vertical ship at (0,0)
        player.getShips().add(createShip(player, ShipType.TRIPLE_DECK, 0, 0, Orientation.VERTICAL));

        // Try to place another ship overlapping at (0,1)
        boolean result = invokeIsValidPlacement(player.getShips(), ShipType.DOUBLE_DECK, 0, 1, Orientation.VERTICAL);

        assertEquals(false, result);
    }

    @Test
    @DisplayName("Should throw exception when ship touches another ship diagonally")
    void shouldThrowExceptionWhenShipTouchesDiagonally() throws Throwable {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        // Place a horizontal ship at (0,0)
        player.getShips().add(createShip(player, ShipType.SINGLE_DECK, 0, 0, Orientation.HORIZONTAL));

        // Try to place another ship diagonally adjacent at (1,1)
        boolean result = invokeIsValidPlacement(player.getShips(), ShipType.SINGLE_DECK, 1, 1, Orientation.HORIZONTAL);

        assertEquals(false, result);
    }

    @Test
    @DisplayName("Should throw exception when ship touches another ship horizontally")
    void shouldThrowExceptionWhenShipTouchesHorizontally() throws Throwable {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        // Place a horizontal ship at (0,0) - occupies (0,0), (1,0), (2,0)
        player.getShips().add(createShip(player, ShipType.TRIPLE_DECK, 0, 0, Orientation.HORIZONTAL));

        // Try to place another ship right next to it at (3,0)
        boolean result = invokeIsValidPlacement(player.getShips(), ShipType.SINGLE_DECK, 3, 0, Orientation.HORIZONTAL);

        assertEquals(false, result);
    }

    @Test
    @DisplayName("Should throw exception when ship touches another ship vertically")
    void shouldThrowExceptionWhenShipTouchesVertically() throws Throwable {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        // Place a vertical ship at (0,0) - occupies (0,0), (0,1), (0,2)
        player.getShips().add(createShip(player, ShipType.TRIPLE_DECK, 0, 0, Orientation.VERTICAL));

        // Try to place another ship right next to it at (1,0)
        boolean result = invokeIsValidPlacement(player.getShips(), ShipType.SINGLE_DECK, 1, 0, Orientation.HORIZONTAL);

        assertEquals(false, result);
    }

    @Test
    @DisplayName("Should throw exception when ship touches another ship below")
    void shouldThrowExceptionWhenShipTouchesBelow() throws Throwable {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        // Place a vertical ship at (0,0) - occupies (0,0), (0,1), (0,2)
        player.getShips().add(createShip(player, ShipType.TRIPLE_DECK, 0, 0, Orientation.VERTICAL));

        // Try to place another ship right below it at (0,3)
        boolean result = invokeIsValidPlacement(player.getShips(), ShipType.SINGLE_DECK, 0, 3, Orientation.HORIZONTAL);

        assertEquals(false, result);
    }

    @Test
    @DisplayName("Should pass validation when ships are properly spaced")
    void shouldPassValidationWhenShipsAreProperlySpaced() throws Throwable {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        // Place a ship at (0,0)
        player.getShips().add(createShip(player, ShipType.SINGLE_DECK, 0, 0, Orientation.HORIZONTAL));

        // Place another ship with proper spacing at (2,2) - at least 1 cell gap
        boolean result = invokeIsValidPlacement(player.getShips(), ShipType.SINGLE_DECK, 2, 2, Orientation.HORIZONTAL);
        assertEquals(true, result);
    }

    @Test
    @DisplayName("Should pass validation when placing multiple ships with proper spacing")
    void shouldPassValidationForMultipleShipsWithProperSpacing() throws Throwable {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        // Place first ship
        player.getShips().add(createShip(player, ShipType.QUADRO_DECK, 0, 0, Orientation.HORIZONTAL));

        // Place second ship with proper spacing
        player.getShips().add(createShip(player, ShipType.TRIPLE_DECK, 0, 2, Orientation.HORIZONTAL));

        // Place third ship
        player.getShips().add(createShip(player, ShipType.DOUBLE_DECK, 0, 4, Orientation.HORIZONTAL));

        // Try to place fourth ship with proper spacing
        boolean result = invokeIsValidPlacement(player.getShips(), ShipType.SINGLE_DECK, 0, 6, Orientation.HORIZONTAL);
        assertEquals(true, result);
    }

    @Test
    @DisplayName("Should allow placing ships of different types up to their limits")
    void shouldAllowPlacingDifferentShipTypesUpToLimits() throws Throwable {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        // Place 1 QUADRO_DECK ship (limit is 1)
        player.getShips().add(createShip(player, ShipType.QUADRO_DECK, 0, 0, Orientation.HORIZONTAL));

        // Should allow placing another type
        boolean result = invokeIsValidPlacement(player.getShips(), ShipType.TRIPLE_DECK, 0, 2, Orientation.HORIZONTAL);
        assertEquals(true, result);
    }

    @Test
    @DisplayName("Should pass validation when placing ships in corners with proper spacing")
    void shouldPassValidationForCornerPlacements() throws Throwable {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        // Place a ship at (0,0)
        player.getShips().add(createShip(player, ShipType.SINGLE_DECK, 0, 0, Orientation.HORIZONTAL));

        // Place another ship at opposite corner (9,9) - assuming 10x10 grid
        boolean result = invokeIsValidPlacement(player.getShips(), ShipType.SINGLE_DECK, 9, 9, Orientation.HORIZONTAL);
        assertEquals(true, result);
    }

    @Test
    @DisplayName("Should throw exception when horizontal ship touches vertical ship")
    void shouldThrowExceptionWhenHorizontalShipTouchesVerticalShip() throws Throwable {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        // Place a vertical ship at (3,0) - occupies (3,0), (3,1), (3,2), (3,3)
        player.getShips().add(createShip(player, ShipType.QUADRO_DECK, 3, 0, Orientation.VERTICAL));

        // Try to place horizontal ship at (2,1) - would touch the vertical ship
        boolean result = invokeIsValidPlacement(player.getShips(), ShipType.DOUBLE_DECK, 2, 1, Orientation.HORIZONTAL);
        assertEquals(false, result);
    }

    // ==================== validateCoordinates Tests ====================

    @Test
    @DisplayName("validateCoordinates: Should pass for valid coordinates")
    void shouldPassValidationForValidCoordinates() throws Throwable {
        // Test valid positions for different ship types and orientations
        assertEquals(true, invokeIsValidCoordinates(0, 0, ShipType.SINGLE_DECK, Orientation.HORIZONTAL));
        assertEquals(true, invokeIsValidCoordinates(5, 5, ShipType.TRIPLE_DECK, Orientation.VERTICAL));
        assertEquals(true, invokeIsValidCoordinates(9, 9, ShipType.SINGLE_DECK, Orientation.HORIZONTAL));
        assertEquals(true, invokeIsValidCoordinates(6, 0, ShipType.QUADRO_DECK, Orientation.HORIZONTAL));
        assertEquals(true, invokeIsValidCoordinates(0, 6, ShipType.QUADRO_DECK, Orientation.VERTICAL));
    }

    @Test
    @DisplayName("validateCoordinates: Should throw exception for negative coordinates")
    void shouldThrowExceptionForNegativeCoordinates() throws Throwable {
        assertEquals(false, invokeIsValidCoordinates(-1, 5, ShipType.SINGLE_DECK, Orientation.HORIZONTAL));
        assertEquals(false, invokeIsValidCoordinates(5, -1, ShipType.SINGLE_DECK, Orientation.VERTICAL));
    }

    @Test
    @DisplayName("validateCoordinates: Should throw exception for coordinates >= 10")
    void shouldThrowExceptionForCoordinatesOutOfBounds() throws Throwable {
        assertEquals(false, invokeIsValidCoordinates(10, 5, ShipType.SINGLE_DECK, Orientation.HORIZONTAL));
        assertEquals(false, invokeIsValidCoordinates(5, 10, ShipType.SINGLE_DECK, Orientation.VERTICAL));
    }

    @Test
    @DisplayName("validateCoordinates: Should throw exception when horizontal ship exceeds board width")
    void shouldThrowExceptionWhenHorizontalShipExceedsBounds() throws Throwable {
        // DOUBLE_DECK (size 2) at x=9 would occupy 9,10 (invalid)
        assertEquals(false, invokeIsValidCoordinates(9, 5, ShipType.DOUBLE_DECK, Orientation.HORIZONTAL));

        // QUADRO_DECK (size 4) at x=7 would occupy 7,8,9,10 (invalid)
        assertEquals(false, invokeIsValidCoordinates(7, 0, ShipType.QUADRO_DECK, Orientation.HORIZONTAL));
    }

    @Test
    @DisplayName("validateCoordinates: Should throw exception when vertical ship exceeds board height")
    void shouldThrowExceptionWhenVerticalShipExceedsBounds() throws Throwable {
        // TRIPLE_DECK (size 3) at y=8 would occupy 8,9,10 (invalid)
        assertEquals(false, invokeIsValidCoordinates(5, 8, ShipType.TRIPLE_DECK, Orientation.VERTICAL));

        // QUADRO_DECK (size 4) at y=7 would occupy 7,8,9,10 (invalid)
        assertEquals(false, invokeIsValidCoordinates(0, 7, ShipType.QUADRO_DECK, Orientation.VERTICAL));
    }

    @Test
    @DisplayName("validateCoordinates: Should pass for ships at edge positions with valid orientations")
    void shouldPassValidationForShipsAtEdge() throws Throwable {
        // QUADRO_DECK horizontal at x=6 occupies 6,7,8,9 (valid)
        assertEquals(true, invokeIsValidCoordinates(6, 0, ShipType.QUADRO_DECK, Orientation.HORIZONTAL));

        // QUADRO_DECK vertical at y=6 occupies 6,7,8,9 (valid)
        assertEquals(true, invokeIsValidCoordinates(0, 6, ShipType.QUADRO_DECK, Orientation.VERTICAL));

        // TRIPLE_DECK horizontal at x=7 occupies 7,8,9 (valid)
        assertEquals(true, invokeIsValidCoordinates(7, 5, ShipType.TRIPLE_DECK, Orientation.HORIZONTAL));

        // TRIPLE_DECK vertical at y=7 occupies 7,8,9 (valid)
        assertEquals(true, invokeIsValidCoordinates(5, 7, ShipType.TRIPLE_DECK, Orientation.VERTICAL));
    }
}

