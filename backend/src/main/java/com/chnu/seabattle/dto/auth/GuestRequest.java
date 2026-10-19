package com.chnu.seabattle.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record GuestRequest(
        @NotBlank(message = "Username is required")
        String username
) {
}
