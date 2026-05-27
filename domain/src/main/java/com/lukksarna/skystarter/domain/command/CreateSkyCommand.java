package com.lukksarna.skystarter.domain.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Axon 5: routing happens at the handler boundary via
 * {@code @InjectEntity(idProperty = "skyId")} on the command-handler parameter,
 * not via an annotation on the command field. The skyId field stays plain.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateSkyCommand {

    private UUID skyId;
    private String name;
}
