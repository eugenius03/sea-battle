package com.chnu.seabattle.converter;

import com.chnu.seabattle.dto.auth.UserRegistrationRequest;
import com.chnu.seabattle.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserConverter {

    @Mapping(target = "passwordHash", source = "password")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    User toEntity(UserRegistrationRequest userDto);
}
