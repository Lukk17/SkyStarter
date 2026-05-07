package com.lukksarna.skystarter.domain.aggregate;

import com.lukksarna.skystarter.domain.command.CreateSkyCommand;
import com.lukksarna.skystarter.domain.command.DeleteSkyCommand;
import com.lukksarna.skystarter.domain.command.UpdateSkyCommand;
import com.lukksarna.skystarter.domain.event.SkyCreatedEvent;
import com.lukksarna.skystarter.domain.event.SkyDeletedEvent;
import com.lukksarna.skystarter.domain.event.SkyUpdatedEvent;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link SkyAggregate} (state) and {@link SkyCommandHandlers}
 * (command handlers) under the Axon 5 Entity Model.
 *
 * Axon 4's {@code AggregateTestFixture} (org.axonframework.test.aggregate.*)
 * was replaced in Axon 5 with {@code AxonTestFixture} that requires a full
 * {@code ApplicationConfigurer} setup. For domain-level unit tests covering
 * validation + event-emission invariants, that's overkill — we mock the
 * {@link EventAppender}, drive the command handler with a freshly-loaded or
 * pre-evolved state, and verify the right events were appended.
 */
@ExtendWith(MockitoExtension.class)
class SkyAggregateTest {

    private static final UUID SKY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private EventAppender eventAppender;

    private SkyAggregate state;
    private SkyCommandHandlers handlers;

    @BeforeEach
    void setUp() {
        state = new SkyAggregate();
        handlers = new SkyCommandHandlers();
    }

    @Test
    void createSky_emitsCreatedEvent() {
        handlers.handle(new CreateSkyCommand(SKY_ID, "Orion"), state, eventAppender);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventAppender).append(captor.capture());
        assertThat(captor.getValue())
                .isInstanceOf(SkyCreatedEvent.class)
                .extracting("skyId", "name")
                .containsExactly(SKY_ID, "Orion");
    }

    @Test
    void createSky_blankName_rejectedAndNoEventAppended() {
        assertThatThrownBy(() ->
                handlers.handle(new CreateSkyCommand(SKY_ID, " "), state, eventAppender))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sky name cannot be empty");

        verifyNoInteractions(eventAppender);
    }

    @Test
    void createSky_skipsWhenAlreadyCreated() {
        state.on(new SkyCreatedEvent(SKY_ID, "Orion"));

        handlers.handle(new CreateSkyCommand(SKY_ID, "Orion-2"), state, eventAppender);

        verifyNoInteractions(eventAppender);
    }

    @Test
    void createSky_eventSourcingHandlerSetsState() {
        state.on(new SkyCreatedEvent(SKY_ID, "Orion"));

        assertThat(state.getSkyId()).isEqualTo(SKY_ID);
        assertThat(state.getName()).isEqualTo("Orion");
        assertThat(state.getStatus()).isEqualTo("CREATED");
    }

    @Test
    void updateSky_emitsUpdatedEvent() {
        state.on(new SkyCreatedEvent(SKY_ID, "Orion"));

        handlers.handle(new UpdateSkyCommand(SKY_ID, "Orion-2"), state, eventAppender);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventAppender).append(captor.capture());
        assertThat(captor.getValue())
                .isInstanceOf(SkyUpdatedEvent.class)
                .extracting("skyId", "name")
                .containsExactly(SKY_ID, "Orion-2");
    }

    @Test
    void updateSky_blankName_rejectedAndNoEventAppended() {
        state.on(new SkyCreatedEvent(SKY_ID, "Orion"));

        assertThatThrownBy(() ->
                handlers.handle(new UpdateSkyCommand(SKY_ID, ""), state, eventAppender))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(eventAppender);
    }

    @Test
    void updateSky_eventSourcingHandlerUpdatesName() {
        state.on(new SkyCreatedEvent(SKY_ID, "Orion"));

        state.on(new SkyUpdatedEvent(SKY_ID, "Orion-2"));

        assertThat(state.getName()).isEqualTo("Orion-2");
    }

    @Test
    void deleteSky_emitsDeletedEventAndMarksDeleted() {
        state.on(new SkyCreatedEvent(SKY_ID, "Orion"));

        handlers.handle(new DeleteSkyCommand(SKY_ID), state, eventAppender);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventAppender).append(captor.capture());
        assertThat(captor.getValue())
                .isInstanceOf(SkyDeletedEvent.class)
                .extracting("skyId")
                .isEqualTo(SKY_ID);

        state.on(new SkyDeletedEvent(SKY_ID));
        assertThat(state.getStatus()).isEqualTo("DELETED");
    }
}
