package com.lukksarna.skystarter.service;

import com.lukksarna.skystarter.domain.command.CreateSkyCommand;
import com.lukksarna.skystarter.domain.command.DeleteSkyCommand;
import com.lukksarna.skystarter.domain.command.UpdateSkyCommand;
import com.lukksarna.skystarter.domain.port.SkyCommandService;
import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Adapts Axon 5's CommandGateway to the domain's CompletableFuture-based ports.
 *
 * Axon 5 changed gateway semantics: send(cmd) returns CommandResult; the
 * CompletableFuture overload requires a Class<R> for the expected result type.
 * Our handlers are void (they emit events via EventAppender, no return value),
 * so we send with Object.class and discard the result, mapping to the
 * port-shaped UUID / Void return.
 */
@RequiredArgsConstructor
public class SkyCommandServicePrimary implements SkyCommandService {

    private final CommandGateway commandGateway;

    public CompletableFuture<UUID> createSky(String name) {
        UUID skyId = UUID.randomUUID();
        return commandGateway.send(new CreateSkyCommand(skyId, name), Object.class)
                .thenApply(ignored -> skyId);
    }

    public CompletableFuture<Void> updateSky(UUID skyId, String name) {
        return commandGateway.send(new UpdateSkyCommand(skyId, name), Object.class)
                .thenApply(ignored -> null);
    }

    public CompletableFuture<Void> deleteSky(UUID skyId) {
        return commandGateway.send(new DeleteSkyCommand(skyId), Object.class)
                .thenApply(ignored -> null);
    }
}
