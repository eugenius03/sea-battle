package com.chnu.seabattle.dto.move;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.Range;

import java.util.UUID;

public record MoveRequest(
        @Positive
        @NotNull
        Long matchId,

        @NotNull
        UUID shooterId,

        @Range(min = 0, max = 9)
        int x,

        @Range(min = 0, max = 9)
        int y
) {
}
