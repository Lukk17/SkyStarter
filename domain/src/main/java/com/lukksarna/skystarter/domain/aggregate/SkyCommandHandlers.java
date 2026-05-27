package com.lukksarna.skystarter.domain.aggregate;

import com.lukksarna.skystarter.domain.command.CreateSkyCommand;
import com.lukksarna.skystarter.domain.command.DeleteSkyCommand;
import com.lukksarna.skystarter.domain.command.UpdateSkyCommand;
import com.lukksarna.skystarter.domain.event.SkyCreatedEvent;
import com.lukksarna.skystarter.domain.event.SkyDeletedEvent;
import com.lukksarna.skystarter.domain.event.SkyUpdatedEvent;
import com.lukksarna.skystarter.domain.service.SkyValidator;
import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;

/**
 * Stateless Axon 5 command handlers for {@link SkyAggregate}.
 *
 * In Axon 5 command handlers are external methods rather than methods on the
 * entity. The framework loads the entity's state by replaying its event stream
 * (matched via {@code @EventSourcedEntity#tagKey} + {@code @EventTag} on the
 * event id field) and injects it through {@code @InjectEntity(idProperty)}.
 * The handler then validates, decides, and emits new events through the
 * injected {@link EventAppender}.
 */
@Component
@RequiredArgsConstructor
public class SkyCommandHandlers {

    private final SkyValidator validator;

    @CommandHandler
    public void handle(CreateSkyCommand command,
                       @InjectEntity(idProperty = "skyId") SkyAggregate state,
                       EventAppender eventAppender) {
        if (state.getStatus() != null) {
            // Already created (or deleted); idempotent guard, no event.
            return;
        }
        validator.validateName(command.getName());
        eventAppender.append(new SkyCreatedEvent(command.getSkyId(), command.getName()));
    }

    // state is required so Axon sources the aggregate, but update/delete decide
    // purely from the command, so the loaded state is intentionally unread.
    @SuppressWarnings("unused")
    @CommandHandler
    public void handle(UpdateSkyCommand command,
                       @InjectEntity(idProperty = "skyId") SkyAggregate state,
                       EventAppender eventAppender) {
        validator.validateName(command.getName());
        eventAppender.append(new SkyUpdatedEvent(command.getSkyId(), command.getName()));
    }

    @SuppressWarnings("unused")
    @CommandHandler
    public void handle(DeleteSkyCommand command,
                       @InjectEntity(idProperty = "skyId") SkyAggregate state,
                       EventAppender eventAppender) {
        eventAppender.append(new SkyDeletedEvent(command.getSkyId()));
    }
}
