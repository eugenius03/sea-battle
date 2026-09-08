package com.chnu.seabattle.dto.auth;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String username
) {
}
