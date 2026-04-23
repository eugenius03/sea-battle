package com.chnu.seabattle.converter;

import com.chnu.seabattle.dto.ship.ShipResponse;
import com.chnu.seabattle.entity.Ship;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ShipConverter {

    ShipResponse toResponse(Ship ship);
}
