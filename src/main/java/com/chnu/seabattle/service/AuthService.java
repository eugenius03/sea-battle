package com.chnu.seabattle.service;

import com.chnu.seabattle.dto.auth.UserLoginRequest;
import com.chnu.seabattle.dto.auth.UserRegistrationRequest;
import com.chnu.seabattle.entity.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;

public interface AuthService {
    @Transactional
    User register(@Valid UserRegistrationRequest registrationRequest);

    @Transactional(readOnly = true)
    UserDetails login(@Valid UserLoginRequest loginRequest);

    ResponseCookie refresh(String refreshToken);
}
