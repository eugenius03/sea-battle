package com.chnu.seabattle.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@AllArgsConstructor
public class ApiError {
    private final String message;
    private final int statusCode;
    private final ZonedDateTime timestamp;
}