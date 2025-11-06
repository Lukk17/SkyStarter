package com.lukksarna.skystarter.domain.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSkyCommand {

    @TargetAggregateIdentifier
    private UUID skyId;
    private String name;
}
