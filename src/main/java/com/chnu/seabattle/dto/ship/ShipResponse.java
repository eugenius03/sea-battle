package com.chnu.seabattle.dto.ship;

import com.chnu.seabattle.entity.Orientation;
import com.chnu.seabattle.entity.ShipType;

public record ShipResponse(Long id, ShipType shipType, int startX, int startY, Orientation orientation) {
}