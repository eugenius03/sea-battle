package com.chnu.seabattle.controller;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.converter.UserConverter;
import com.chnu.seabattle.dto.user.PlayerCombatStats;
import com.chnu.seabattle.dto.user.PublicUserResponse;
import com.chnu.seabattle.dto.user.UserResponse;
import com.chnu.seabattle.entity.User;
import com.chnu.seabattle.exception.ResourceNotFoundException;
import com.chnu.seabattle.service.UserService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserConverter userConverter;

    @GetMapping("/me")
    public UserResponse getCurrentUser() {
        User user = userService.getAuthenticatedUser();
        PlayerCombatStats stats = userService.getStats(user.getId());
        return userConverter.toDto(user, stats);

    }

    @GetMapping("/{username}")
    public PublicUserResponse getCurrentUser(@NonNull @PathVariable String username) {
        User user = userService.findByUsername(username).orElseThrow(
                () -> new ResourceNotFoundException(ErrorConstants.USER_NOT_FOUND)
        );
        PlayerCombatStats stats = userService.getStats(user.getId());
        return userConverter.toPublicDto(user, stats);

    }
}
