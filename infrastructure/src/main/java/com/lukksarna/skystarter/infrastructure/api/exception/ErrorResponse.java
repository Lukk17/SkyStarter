package com.lukksarna.skystarter.infrastructure.api.exception;

public record ErrorResponse(
        String code,
        Object details
) {
}
