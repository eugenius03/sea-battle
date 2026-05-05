package com.chnu.seabattle.service.impl;

import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.entity.Orientation;
import com.chnu.seabattle.entity.Ship;
import com.chnu.seabattle.entity.ShipType;
import com.chnu.seabattle.util.BoardUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@DisplayName("GameServiceImpl - validatePlacement Tests")
class GameServiceImplValidatePlacementTest {

    private MatchPlayer createMatchPlayer(UUID playerId) {
        Match match = new Match();
        match.setId(1L);
        match.setStatus(MatchStatus.PLANNING);
        match.setCurrentPlayerTurnId(playerId);

        MatchPlayer player = new MatchPlayer();
        player.setId(UUID.randomUUID());
        player.setMatch(match);

        player.setUserId(playerId);

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
    void shouldPassValidationForFirstShip() {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        boolean result = BoardUtils.isValidPlacement(player.getShips(), ShipType.QUADRO_DECK, 0, 0, Orientation.HORIZONTAL);
        assertTrue(result);
    }

    @Test
    @DisplayName("Should throw exception when ship overlaps another ship - horizontal overlap")
    void shouldThrowExceptionWhenShipOverlapsHorizontal() {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        player.getShips().add(createShip(player, ShipType.TRIPLE_DECK, 0, 0, Orientation.HORIZONTAL));

        boolean result = BoardUtils.isValidPlacement(player.getShips(), ShipType.DOUBLE_DECK, 1, 0, Orientation.HORIZONTAL);

        assertFalse(result);
    }

    @Test
    @DisplayName("Should throw exception when ship overlaps another ship - vertical overlap")
    void shouldThrowExceptionWhenShipOverlapsVertical() {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        player.getShips().add(createShip(player, ShipType.TRIPLE_DECK, 0, 0, Orientation.VERTICAL));

        boolean result = BoardUtils.isValidPlacement(player.getShips(), ShipType.DOUBLE_DECK, 0, 1, Orientation.VERTICAL);

        assertFalse(result);
    }

    @Test
    @DisplayName("Should throw exception when ship touches another ship diagonally")
    void shouldThrowExceptionWhenShipTouchesDiagonally() {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        player.getShips().add(createShip(player, ShipType.SINGLE_DECK, 0, 0, Orientation.HORIZONTAL));

        boolean result = BoardUtils.isValidPlacement(player.getShips(), ShipType.SINGLE_DECK, 1, 1, Orientation.HORIZONTAL);

        assertFalse(result);
    }

    @Test
    @DisplayName("Should throw exception when ship touches another ship horizontally")
    void shouldThrowExceptionWhenShipTouchesHorizontally() {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        player.getShips().add(createShip(player, ShipType.TRIPLE_DECK, 0, 0, Orientation.HORIZONTAL));

        boolean result = BoardUtils.isValidPlacement(player.getShips(), ShipType.SINGLE_DECK, 3, 0, Orientation.HORIZONTAL);

        assertFalse(result);
    }

    @Test
    @DisplayName("Should throw exception when ship touches another ship vertically")
    void shouldThrowExceptionWhenShipTouchesVertically() {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        player.getShips().add(createShip(player, ShipType.TRIPLE_DECK, 0, 0, Orientation.VERTICAL));

        boolean result = BoardUtils.isValidPlacement(player.getShips(), ShipType.SINGLE_DECK, 1, 0, Orientation.HORIZONTAL);

        assertFalse(result);
    }

    @Test
    @DisplayName("Should throw exception when ship touches another ship below")
    void shouldThrowExceptionWhenShipTouchesBelow() {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        player.getShips().add(createShip(player, ShipType.TRIPLE_DECK, 0, 0, Orientation.VERTICAL));

        boolean result = BoardUtils.isValidPlacement(player.getShips(), ShipType.SINGLE_DECK, 0, 3, Orientation.HORIZONTAL);

        assertFalse(result);
    }

    @Test
    @DisplayName("Should pass validation when ships are properly spaced")
    void shouldPassValidationWhenShipsAreProperlySpaced() {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        // Place a ship at (0,0)

        boolean result = BoardUtils.isValidPlacement(player.getShips(), ShipType.SINGLE_DECK, 2, 2, Orientation.HORIZONTAL);
        assertTrue(result);
    }

    @Test
    @DisplayName("Should pass validation when placing multiple ships with proper spacing")
    void shouldPassValidationForMultipleShipsWithProperSpacing() {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        player.getShips().add(createShip(player, ShipType.QUADRO_DECK, 0, 0, Orientation.HORIZONTAL));

        player.getShips().add(createShip(player, ShipType.TRIPLE_DECK, 0, 2, Orientation.HORIZONTAL));

        player.getShips().add(createShip(player, ShipType.DOUBLE_DECK, 0, 4, Orientation.HORIZONTAL));

        boolean result = BoardUtils.isValidPlacement(player.getShips(), ShipType.SINGLE_DECK, 0, 6, Orientation.HORIZONTAL);
        assertTrue(result);
    }

    @Test
    @DisplayName("Should allow placing ships of different types up to their limits")
    void shouldAllowPlacingDifferentShipTypesUpToLimits() {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        player.getShips().add(createShip(player, ShipType.QUADRO_DECK, 0, 0, Orientation.HORIZONTAL));

        boolean result = BoardUtils.isValidPlacement(player.getShips(), ShipType.TRIPLE_DECK, 0, 2, Orientation.HORIZONTAL);
        assertTrue(result);
    }

    @Test
    @DisplayName("Should pass validation when placing ships in corners with proper spacing")
    void shouldPassValidationForCornerPlacements() {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        player.getShips().add(createShip(player, ShipType.SINGLE_DECK, 0, 0, Orientation.HORIZONTAL));

        boolean result = BoardUtils.isValidPlacement(player.getShips(), ShipType.SINGLE_DECK, 9, 9, Orientation.HORIZONTAL);
        assertTrue(result);
    }

    @Test
    @DisplayName("Should throw exception when horizontal ship touches vertical ship")
    void shouldThrowExceptionWhenHorizontalShipTouchesVerticalShip() {
        UUID playerId = UUID.randomUUID();
        MatchPlayer player = createMatchPlayer(playerId);

        player.getShips().add(createShip(player, ShipType.QUADRO_DECK, 3, 0, Orientation.VERTICAL));

        boolean result = BoardUtils.isValidPlacement(player.getShips(), ShipType.DOUBLE_DECK, 2, 1, Orientation.HORIZONTAL);
        assertFalse(result);
    }

    // ==================== validateCoordinates Tests ====================

    @Test
    @DisplayName("validateCoordinates: Should pass for valid coordinates")
    void shouldPassValidationForValidCoordinates() {
        assertTrue(BoardUtils.isWithinBounds(0, 0, ShipType.SINGLE_DECK, Orientation.HORIZONTAL));
        assertTrue(BoardUtils.isWithinBounds(5, 5, ShipType.TRIPLE_DECK, Orientation.VERTICAL));
        assertTrue(BoardUtils.isWithinBounds(9, 9, ShipType.SINGLE_DECK, Orientation.HORIZONTAL));
        assertTrue(BoardUtils.isWithinBounds(6, 0, ShipType.QUADRO_DECK, Orientation.HORIZONTAL));
        assertTrue(BoardUtils.isWithinBounds(0, 6, ShipType.QUADRO_DECK, Orientation.VERTICAL));
    }

    @Test
    @DisplayName("validateCoordinates: Should throw exception for negative coordinates")
    void shouldThrowExceptionForNegativeCoordinates() {
        assertFalse(BoardUtils.isWithinBounds(-1, 5, ShipType.SINGLE_DECK, Orientation.HORIZONTAL));
        assertFalse(BoardUtils.isWithinBounds(5, -1, ShipType.SINGLE_DECK, Orientation.VERTICAL));
    }

    @Test
    @DisplayName("validateCoordinates: Should throw exception for coordinates >= 10")
    void shouldThrowExceptionForCoordinatesOutOfBounds() {
        assertFalse(BoardUtils.isWithinBounds(10, 5, ShipType.SINGLE_DECK, Orientation.HORIZONTAL));
        assertFalse(BoardUtils.isWithinBounds(5, 10, ShipType.SINGLE_DECK, Orientation.VERTICAL));
    }

    @Test
    @DisplayName("validateCoordinates: Should throw exception when horizontal ship exceeds board width")
    void shouldThrowExceptionWhenHorizontalShipExceedsBounds() {
        assertFalse(BoardUtils.isWithinBounds(9, 5, ShipType.DOUBLE_DECK, Orientation.HORIZONTAL));

        assertFalse(BoardUtils.isWithinBounds(7, 0, ShipType.QUADRO_DECK, Orientation.HORIZONTAL));
    }

    @Test
    @DisplayName("validateCoordinates: Should throw exception when vertical ship exceeds board height")
    void shouldThrowExceptionWhenVerticalShipExceedsBounds() {
        assertFalse(BoardUtils.isWithinBounds(5, 8, ShipType.TRIPLE_DECK, Orientation.VERTICAL));

        assertFalse(BoardUtils.isWithinBounds(0, 7, ShipType.QUADRO_DECK, Orientation.VERTICAL));
    }

    @Test
    @DisplayName("validateCoordinates: Should pass for ships at edge positions with valid orientations")
    void shouldPassValidationForShipsAtEdge() {
        assertTrue(BoardUtils.isWithinBounds(6, 0, ShipType.QUADRO_DECK, Orientation.HORIZONTAL));

        assertTrue(BoardUtils.isWithinBounds(0, 6, ShipType.QUADRO_DECK, Orientation.VERTICAL));

        assertTrue(BoardUtils.isWithinBounds(7, 5, ShipType.TRIPLE_DECK, Orientation.HORIZONTAL));

        assertTrue(BoardUtils.isWithinBounds(5, 7, ShipType.TRIPLE_DECK, Orientation.VERTICAL));
    }
}

