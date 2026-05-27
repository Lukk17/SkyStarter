package com.lukksarna.skystarter.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkyValidatorTest {

    private final SkyValidator validator = new SkyValidator();

    @Test
    void acceptsNonBlankName() {
        assertThatCode(() -> validator.validateName("Andromeda"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void rejectsBlankOrNullName(String name) {
        assertThatThrownBy(() -> validator.validateName(name))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sky name cannot be empty");
    }
}
