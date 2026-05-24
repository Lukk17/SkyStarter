package com.lukksarna.skystarter.service;

import com.lukksarna.skystarter.domain.command.CreateSkyCommand;
import com.lukksarna.skystarter.domain.command.DeleteSkyCommand;
import com.lukksarna.skystarter.domain.command.UpdateSkyCommand;
import com.lukksarna.skystarter.domain.port.SkyCommandService;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.slf4j.MDC;

/**
 * Adapts Axon 5's CommandGateway to the domain's CompletableFuture-based ports.
 *
 * Axon 5 changed gateway semantics: send(cmd) returns CommandResult; the
 * CompletableFuture overload requires a Class<R> for the expected result type.
 * Our handlers are void (they emit events via EventAppender, no return value),
 * so we send with Object.class and discard the result, mapping to the
 * port-shaped UUID / Void return.
 */
@Slf4j
@RequiredArgsConstructor
public class SkyCommandServicePrimary implements SkyCommandService {

    private static final String JWT_SUBJECT_MDC_KEY = "jwt.subject";

    private final CommandGateway commandGateway;

    public CompletableFuture<UUID> createSky(String name) {
        UUID skyId = UUID.randomUUID();
        String subject = MDC.get(JWT_SUBJECT_MDC_KEY);
        long startNs = System.nanoTime();
        logAuditStart("sky.create", skyId, subject);

        return commandGateway.send(new CreateSkyCommand(skyId, name), Object.class)
                .thenApply(ignored -> skyId)
                .whenComplete((id, ex) -> logAuditCompletion("sky.create", skyId, subject, startNs, ex));
    }

    public CompletableFuture<Void> updateSky(UUID skyId, String name) {
        String subject = MDC.get(JWT_SUBJECT_MDC_KEY);
        long startNs = System.nanoTime();
        logAuditStart("sky.update", skyId, subject);

        return commandGateway.send(new UpdateSkyCommand(skyId, name), Object.class)
                .<Void>thenApply(ignored -> null)
                .whenComplete((ignored, ex) -> logAuditCompletion("sky.update", skyId, subject, startNs, ex));
    }

    public CompletableFuture<Void> deleteSky(UUID skyId) {
        String subject = MDC.get(JWT_SUBJECT_MDC_KEY);
        long startNs = System.nanoTime();
        logAuditStart("sky.delete", skyId, subject);

        return commandGateway.send(new DeleteSkyCommand(skyId), Object.class)
                .<Void>thenApply(ignored -> null)
                .whenComplete((ignored, ex) -> logAuditCompletion("sky.delete", skyId, subject, startNs, ex));
    }

    private static void logAuditStart(String operation, UUID skyId, String subject) {
        log.atInfo()
                .setMessage("audit start")
                .addKeyValue("operation", operation)
                .addKeyValue("skyId", skyId)
                .addKeyValue("subject", subject)
                .log();
    }

    private static void logAuditCompletion(String operation, UUID skyId, String subject, long startNs, Throwable ex) {
        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
        if (ex != null) {
            log.atWarn()
                    .setMessage("audit complete")
                    .addKeyValue("operation", operation)
                    .addKeyValue("outcome", "failure")
                    .addKeyValue("skyId", skyId)
                    .addKeyValue("subject", subject)
                    .addKeyValue("durationMs", durationMs)
                    .addKeyValue("error", ex.getClass().getSimpleName())
                    .log();
            return;
        }
        log.atInfo()
                .setMessage("audit complete")
                .addKeyValue("operation", operation)
                .addKeyValue("outcome", "success")
                .addKeyValue("skyId", skyId)
                .addKeyValue("subject", subject)
                .addKeyValue("durationMs", durationMs)
                .log();
    }
}
