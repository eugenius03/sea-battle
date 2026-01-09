package com.chnu.seabattle.converter;

import com.chnu.seabattle.dto.UserRegistrationRequest;
import com.chnu.seabattle.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserConverter {

    @Mapping(target = "passwordHash", source = "password")
    User toEntity(UserRegistrationRequest userDto);
}
