package com.chnu.seabattle.service.serviceImpl;

import com.chnu.seabattle.entity.User;
import com.chnu.seabattle.repository.UserRepository;
import com.chnu.seabattle.service.BaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends BaseService<User, UUID> {

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

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public boolean checkPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public boolean existsByUsername(String username) {
        return userRepository.findByUsername(username).isPresent();
    }
}

