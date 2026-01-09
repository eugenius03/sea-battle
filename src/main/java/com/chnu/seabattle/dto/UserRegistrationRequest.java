package com.chnu.seabattle.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserRegistrationRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @Email
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
