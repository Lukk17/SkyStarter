package com.lukksarna.skystarter.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkyCreatedEvent {

    private UUID skyId;
    private String name;
}
