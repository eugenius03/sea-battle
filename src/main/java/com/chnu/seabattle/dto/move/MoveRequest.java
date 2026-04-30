package com.chnu.seabattle.dto.move;

import com.chnu.seabattle.entity.MoveType;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Range;

import java.util.UUID;

public record MoveRequest(
        @NotNull
        UUID shooterId,

        @Range(min = 0, max = 9)
        int x,

        @Range(min = 0, max = 9)
        int y,

        MoveType moveType
) {
}
