package com.chnu.seabattle.converter;

import com.chnu.seabattle.dto.ship.ShipRequest;
import com.chnu.seabattle.dto.ship.ShipResponse;
import com.chnu.seabattle.entity.Ship;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ShipConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hits", ignore = true)
    @Mapping(target = "isSunk", ignore = true)
    @Mapping(target = "matchPlayer", ignore = true)
    Ship toEntity(ShipRequest shipRequest);

    ShipResponse toResponse(Ship ship);
}
