package com.chnu.seabattle.dto.ship;

import com.chnu.seabattle.entity.Orientation;
import com.chnu.seabattle.entity.ShipType;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Range;

import java.io.Serializable;

public record ShipRequest(
        @NotNull(message = "shipType can't be null") ShipType shipType,
        @Range(message = "Coordinates out of bounds", min = 0, max = 9) int startX,
        @Range(message = "Coordinates out of bounds", min = 0, max = 9) int startY,
        @NotNull(message = "orientation can't be null") Orientation orientation) implements Serializable {
}