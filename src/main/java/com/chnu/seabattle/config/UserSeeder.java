package com.chnu.seabattle.config;

import com.chnu.seabattle.entity.User;
import com.chnu.seabattle.service.serviceImpl.UserServiceImpl;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserSeeder {

    private final UserServiceImpl userService;

    @PostConstruct
    public void seedAuthorities() {
        createIfMissing("vas");
        createIfMissing("vas1");

    }

    private void createIfMissing(String name) {
        if (!userService.existsByUsername(name)) {
            userService.create(
                    User.builder()
                            .username(name)
                            .passwordHash("123123")
                            .build()
            );
        }
    }
}


