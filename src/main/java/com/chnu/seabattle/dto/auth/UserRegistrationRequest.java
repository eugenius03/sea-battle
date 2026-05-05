package com.chnu.seabattle.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserRegistrationRequest(
        @NotBlank(message = "Username is required")
        String username,

        @Email
        String email,

        @NotBlank(message = "Password is required")
        String password
) {


}
