package com.lukksarna.skystarter.service;

import com.lukksarna.skystarter.domain.command.CreateSkyCommand;
import com.lukksarna.skystarter.domain.command.DeleteSkyCommand;
import com.lukksarna.skystarter.domain.command.UpdateSkyCommand;
import com.lukksarna.skystarter.domain.port.SkyCommandService;
import lombok.RequiredArgsConstructor;
import org.axonframework.commandhandling.gateway.CommandGateway;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class SkyCommandServicePrimary implements SkyCommandService {

    private final CommandGateway commandGateway;

    public CompletableFuture<UUID> createSky(String name) {
        UUID skyId = UUID.randomUUID();
        return commandGateway.send(new CreateSkyCommand(skyId, name));
    }

    public CompletableFuture<Void> updateSky(UUID skyId, String name) {
        return commandGateway.send(new UpdateSkyCommand(skyId, name));
    }

    public CompletableFuture<Void> deleteSky(UUID skyId) {
        return commandGateway.send(new DeleteSkyCommand(skyId));
    }
}
