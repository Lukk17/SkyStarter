package com.lukksarna.domain.service;

import com.lukksarna.domain.model.Starter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class StarterValidator {

    public void validate(Starter starter) {
        validateValue(starter.value());
    }

    private void validateValue(String value) {
        if (null == value) {
            throw new IllegalArgumentException("value cannot be null");
        }
    }
}
