package com.lukksarna.skystarter.domain.aggregate;

import com.lukksarna.skystarter.domain.event.SkyCreatedEvent;
import com.lukksarna.skystarter.domain.event.SkyDeletedEvent;
import com.lukksarna.skystarter.domain.event.SkyUpdatedEvent;
import lombok.Getter;
import lombok.Setter;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;

import java.util.UUID;

/**
 * Sky aggregate state, modelled with the Axon 5 Entity Model + Spring stereotype.
 *
 * The {@link EventSourced} annotation is the Spring-aware companion to
 * {@code @EventSourcedEntity}: the {@code SpringEventSourcedEntityLookup}
 * post-processor scans for it and registers a {@code Repository<SkyAggregate, UUID>}
 * with the Axon configuration. The {@code idType} attribute is mandatory —
 * without it the lookup throws "No repository was registered for the given
 * entity type with id type [UUID]". The {@code tagKey} attribute pairs with
 * {@code @EventTag} on event id fields so the event store can resolve which
 * stream an event belongs to.
 *
 * Command handlers live in {@link SkyCommandHandlers} (Axon 5 keeps them
 * outside the entity); this class is a pure state object with an
 * {@link EntityCreator} constructor and {@link EventSourcingHandler} methods.
 */
@Getter
@Setter
@EventSourced(idType = UUID.class, tagKey = "skyId")
public class SkyAggregate {

    private UUID skyId;
    private String name;
    private String status;

    @EntityCreator
    public SkyAggregate() {
    }

    @EventSourcingHandler
    public void on(SkyCreatedEvent event) {
        this.skyId = event.getSkyId();
        this.name = event.getName();
        this.status = "CREATED";
    }

    @EventSourcingHandler
    public void on(SkyUpdatedEvent event) {
        this.name = event.getName();
    }

    @EventSourcingHandler
    public void on(SkyDeletedEvent event) {
        this.status = "DELETED";
    }
}
