package com.lukksarna.skystarter.infrastructure.api.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateSkyRequest {

    @NotBlank(message = "Sky name must not be blank")
    @Size(max = 255, message = "Sky name must be at most 255 characters")
    private String name;
}
