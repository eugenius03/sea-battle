package com.chnu.seabattle.util;

import com.chnu.seabattle.entity.Orientation;
import com.chnu.seabattle.entity.Ship;
import com.chnu.seabattle.entity.ShipType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BoardUtils - isValidFleet Tests")
class BoardUtilsFleetTest {

    private static Ship ship(ShipType type, int x, int y, Orientation orientation) {
        return Ship.builder().shipType(type).startX(x).startY(y).orientation(orientation).build();
    }

    private static List<Ship> validFleet() {
        List<Ship> ships = new ArrayList<>();
        ships.add(ship(ShipType.QUADRO_DECK, 0, 0, Orientation.HORIZONTAL));
        ships.add(ship(ShipType.TRIPLE_DECK, 0, 2, Orientation.HORIZONTAL));
        ships.add(ship(ShipType.TRIPLE_DECK, 5, 2, Orientation.HORIZONTAL));
        ships.add(ship(ShipType.DOUBLE_DECK, 0, 4, Orientation.HORIZONTAL));
        ships.add(ship(ShipType.DOUBLE_DECK, 3, 4, Orientation.HORIZONTAL));
        ships.add(ship(ShipType.DOUBLE_DECK, 6, 4, Orientation.HORIZONTAL));
        ships.add(ship(ShipType.SINGLE_DECK, 0, 6, Orientation.HORIZONTAL));
        ships.add(ship(ShipType.SINGLE_DECK, 2, 6, Orientation.HORIZONTAL));
        ships.add(ship(ShipType.SINGLE_DECK, 4, 6, Orientation.HORIZONTAL));
        ships.add(ship(ShipType.SINGLE_DECK, 6, 6, Orientation.HORIZONTAL));
        return ships;
    }

    @Test
    @DisplayName("Accepts a complete, in-bounds, non-touching fleet")
    void acceptsValidFleet() {
        assertTrue(BoardUtils.isValidFleet(validFleet()));
    }

    @Test
    @DisplayName("Rejects an empty fleet")
    void rejectsEmptyFleet() {
        assertFalse(BoardUtils.isValidFleet(new ArrayList<>()));
    }

    @Test
    @DisplayName("Rejects a fleet missing a ship")
    void rejectsMissingShip() {
        List<Ship> ships = validFleet();
        ships.remove(0);
        assertFalse(BoardUtils.isValidFleet(ships));
    }

    @Test
    @DisplayName("Rejects a fleet with an extra ship of one type")
    void rejectsExtraShip() {
        List<Ship> ships = validFleet();
        ships.add(ship(ShipType.SINGLE_DECK, 8, 8, Orientation.HORIZONTAL));
        assertFalse(BoardUtils.isValidFleet(ships));
    }

    @Test
    @DisplayName("Rejects a horizontal double deck at x=9 that runs off the board")
    void rejectsShipRunningOffTheBoard() {
        List<Ship> ships = validFleet();
        ships.set(3, ship(ShipType.DOUBLE_DECK, 9, 0, Orientation.HORIZONTAL));
        assertFalse(BoardUtils.isValidFleet(ships));
    }

    @Test
    @DisplayName("Rejects two ships that touch diagonally")
    void rejectsTouchingShips() {
        List<Ship> ships = validFleet();
        ships.set(9, ship(ShipType.SINGLE_DECK, 4, 3, Orientation.HORIZONTAL));
        assertFalse(BoardUtils.isValidFleet(ships));
    }
}
