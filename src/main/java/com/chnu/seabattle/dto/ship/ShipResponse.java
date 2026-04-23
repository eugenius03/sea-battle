package com.chnu.seabattle.dto.ship;

import com.chnu.seabattle.entity.Orientation;
import com.chnu.seabattle.entity.ShipType;

import java.io.Serializable;

/**
 * DTO for {@link com.chnu.seabattle.entity.Ship}
 */
public record ShipResponse(
        Long id,
        ShipType shipType,
        int startX,
        int startY,
        Orientation orientation
) implements Serializable {
}