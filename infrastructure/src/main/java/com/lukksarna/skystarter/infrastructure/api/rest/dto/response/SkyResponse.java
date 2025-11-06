package com.lukksarna.skystarter.infrastructure.api.rest.dto.response;

import java.util.UUID;


public record SkyResponse(
        UUID skyId,
        String name,
        String status
) {

}
