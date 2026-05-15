package com.chnu.seabattle.service;

import com.chnu.seabattle.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface UserService extends BaseService<User, UUID> {
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    User getAuthenticatedUser();

    boolean existsById(UUID id);
}
