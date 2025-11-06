package com.lukksarna.skystarter.domain.service;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class SkyValidator {

    public void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Sky name cannot be empty.");
        }
    }
}