package com.lukksarna.skystarter.domain.model;

import java.util.UUID;

public record Sky(
        UUID skyId,
        String name,
        String status
) {
}
