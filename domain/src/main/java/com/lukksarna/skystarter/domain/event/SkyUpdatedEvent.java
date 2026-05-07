package com.lukksarna.skystarter.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.axonframework.eventsourcing.annotation.EventTag;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkyUpdatedEvent {

    @EventTag
    private UUID skyId;
    private String name;
}
