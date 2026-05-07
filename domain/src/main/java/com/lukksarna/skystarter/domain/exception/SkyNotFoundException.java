package com.lukksarna.skystarter.domain.exception;

import java.util.UUID;

public class SkyNotFoundException extends RuntimeException {

    public SkyNotFoundException(UUID skyId) {
        super("Sky not found with id: " + skyId);
    }
}
