package com.chnu.seabattle.converter;

import com.chnu.seabattle.dto.auth.UserRegistrationRequest;
import com.chnu.seabattle.dto.user.PlayerCombatStats;
import com.chnu.seabattle.dto.user.PublicUserResponse;
import com.chnu.seabattle.dto.user.UserResponse;
import com.chnu.seabattle.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserConverter {

    @Mapping(target = "passwordHash", source = "password")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "userType", constant = "REGISTERED")
    @Mapping(target = "isEmailVerified", constant = "false")
    User toEntity(UserRegistrationRequest userDto);

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "memberSince", source = "user.createdAt")
    @Mapping(target = "userType", source = "user.userType")
    @Mapping(target = "emailVerified", source = "user.emailVerified")
    @Mapping(target = "stats", source = "stats")
    UserResponse toDto(User user, PlayerCombatStats stats);

    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "memberSince", source = "user.createdAt")
    @Mapping(target = "userType", source = "user.userType")
    @Mapping(target = "stats", source = "stats")
    PublicUserResponse toPublicDto(User user, PlayerCombatStats stats);
}
