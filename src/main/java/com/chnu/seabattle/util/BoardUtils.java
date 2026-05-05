package com.chnu.seabattle.util;

import com.chnu.seabattle.dto.game.Cell;
import com.chnu.seabattle.entity.Orientation;
import com.chnu.seabattle.entity.Ship;
import com.chnu.seabattle.entity.ShipType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BoardUtils {

    private BoardUtils() {
    }

    private static final int GRID_SIZE = 10;

    public static Set<Cell> getOccupiedCells(Ship ship) {
        Set<Cell> cells = new HashSet<>();
        int length = ship.getShipType().getSize();

        for (int i = 0; i < length; i++) {
            int cx = ship.getOrientation() == Orientation.HORIZONTAL
                    ? ship.getStartX() + i
                    : ship.getStartX();

            int cy = ship.getOrientation() == Orientation.VERTICAL
                    ? ship.getStartY() + i
                    : ship.getStartY();

            cells.add(new Cell(cx, cy));
        }
        return cells;
    }

    public static boolean isWithinBounds(int x, int y, ShipType type, Orientation orientation) {
        if (x < 0 || y < 0) return false;
        return Orientation.HORIZONTAL.equals(orientation)
                ? x + type.getSize() <= GRID_SIZE
                : y + type.getSize() <= GRID_SIZE;
    }

    public static boolean isWithinBounds(int x, int y) {
        return x >= 0 && x < GRID_SIZE && y >= 0 && y < GRID_SIZE;
    }

    public static boolean isValidPlacement(List<Ship> ships, ShipType type, int x, int y, Orientation orientation) {
        if (!isWithinBounds(x, y, type, orientation)) {
            return false;
        }
        return ships.stream().noneMatch(ship ->
                overlapsOrTouches(ship, x, y, orientation, type.getSize())
        );
    }

    public static boolean overlapsOrTouches(Ship existing, int x, int y, Orientation orientation, int length) {
        Set<Cell> existingCells = getOccupiedCells(existing);

        for (int i = 0; i < length; i++) {
            int cx = orientation == Orientation.HORIZONTAL ? x + i : x;
            int cy = orientation == Orientation.VERTICAL ? y + i : y;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (existingCells.contains(new Cell(cx + dx, cy + dy))) return true;
                }
            }
        }
        return false;
    }
}
