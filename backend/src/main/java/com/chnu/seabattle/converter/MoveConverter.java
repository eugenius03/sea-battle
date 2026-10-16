package com.chnu.seabattle.converter;

import com.chnu.seabattle.dto.move.MoveResponse;
import com.chnu.seabattle.dto.move.ReplayMoveResponse;
import com.chnu.seabattle.entity.Move;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MoveConverter {

    MoveResponse toResponse(Move entity);

    @Mapping(target = "isMoveOrigin", source = "moveOrigin")
    ReplayMoveResponse toReplayResponse(Move entity);
}
