package com.chnu.seabattle.converter;

import com.chnu.seabattle.dto.move.MoveRequest;
import com.chnu.seabattle.dto.move.MoveResponse;
import com.chnu.seabattle.entity.Move;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MoveConverter {

    Move toEntity(MoveRequest request);

    MoveResponse toResponse(Move entity);
}
