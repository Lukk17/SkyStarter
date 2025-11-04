package com.lukksarna.infrastructure.api.exception;

public record ErrorResponse(
        String code,
        Object details
) {
}
