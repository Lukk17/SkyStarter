package com.lukksarna.skystarter.domain.aggregate;

import com.lukksarna.skystarter.domain.command.CreateSkyCommand;
import com.lukksarna.skystarter.domain.command.DeleteSkyCommand;
import com.lukksarna.skystarter.domain.command.UpdateSkyCommand;
import com.lukksarna.skystarter.domain.event.SkyCreatedEvent;
import com.lukksarna.skystarter.domain.event.SkyDeletedEvent;
import com.lukksarna.skystarter.domain.event.SkyUpdatedEvent;
import com.lukksarna.skystarter.domain.service.SkyValidator;
import lombok.NoArgsConstructor;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.util.UUID;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Aggregate
@NoArgsConstructor
public class SkyAggregate {

    @AggregateIdentifier
    private UUID skyId;
    private String name;
    private String status;

    @CommandHandler
    public SkyAggregate(CreateSkyCommand command) {
        new SkyValidator().validateName(command.getName());
        apply(new SkyCreatedEvent(command.getSkyId(), command.getName()));
    }

    @EventSourcingHandler
    public void on(SkyCreatedEvent event) {
        this.skyId = event.getSkyId();
        this.name = event.getName();
        this.status = "CREATED";
    }

    @CommandHandler
    public void handle(UpdateSkyCommand command) {
        new SkyValidator().validateName(command.getName());
        apply(new SkyUpdatedEvent(command.getSkyId(), command.getName()));
    }

    @EventSourcingHandler
    public void on(SkyUpdatedEvent event) {
        this.name = event.getName();
    }

    @CommandHandler
    public void handle(DeleteSkyCommand command) {
        apply(new SkyDeletedEvent(command.getSkyId()));
    }

    @EventSourcingHandler
    public void on(SkyDeletedEvent event) {
        AggregateLifecycle.markDeleted();
    }
}
