package com.lukksarna.skystarter.domain.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSkyCommand {

    private UUID skyId;
    private String name;
}
