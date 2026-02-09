package com.chnu.seabattle.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ApiException {
    private String message;
    private int statusCode;
    private ZonedDateTime timestamp;
}