package com.chnu.seabattle.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(
        @NotBlank(message = "Token is required")
        String token
) {
}
