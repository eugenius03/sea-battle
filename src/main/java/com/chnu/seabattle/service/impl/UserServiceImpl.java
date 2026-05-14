package com.chnu.seabattle.service.impl;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.entity.User;
import com.chnu.seabattle.exception.ResourceNotFoundException;
import com.chnu.seabattle.exception.UnauthorizedException;
import com.chnu.seabattle.repository.UserRepository;
import com.chnu.seabattle.service.AbstractBaseService;
import com.chnu.seabattle.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends AbstractBaseService<User, UUID> implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    protected JpaRepository<User, UUID> getRepository() {
        return userRepository;
    }

    @Override
    protected void beforeCreate(User entity) {
        entity.setPasswordHash(passwordEncoder.encode(entity.getPasswordHash()));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public User getAuthenticatedUser() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException(ErrorConstants.USER_NOT_AUTHENTICATED);
        }

        String username = authentication.getName();
        return this.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorConstants.AUTHENTICATED_USER_NOT_FOUND
                ));

    }
}

