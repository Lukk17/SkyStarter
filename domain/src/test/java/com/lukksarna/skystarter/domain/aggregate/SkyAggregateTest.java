package com.lukksarna.skystarter.domain.aggregate;

import com.lukksarna.skystarter.domain.command.CreateSkyCommand;
import com.lukksarna.skystarter.domain.command.DeleteSkyCommand;
import com.lukksarna.skystarter.domain.command.UpdateSkyCommand;
import com.lukksarna.skystarter.domain.event.SkyCreatedEvent;
import com.lukksarna.skystarter.domain.event.SkyDeletedEvent;
import com.lukksarna.skystarter.domain.event.SkyUpdatedEvent;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class SkyAggregateTest {

    private FixtureConfiguration<SkyAggregate> fixture;

    private static final UUID SKY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(SkyAggregate.class);
    }

    @Test
    void createSky_emitsCreatedEvent() {
        fixture.givenNoPriorActivity()
                .when(new CreateSkyCommand(SKY_ID, "Orion"))
                .expectEvents(new SkyCreatedEvent(SKY_ID, "Orion"));
    }

    @Test
    void createSky_blankName_rejected() {
        fixture.givenNoPriorActivity()
                .when(new CreateSkyCommand(SKY_ID, " "))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    void updateSky_emitsUpdatedEvent() {
        fixture.given(new SkyCreatedEvent(SKY_ID, "Orion"))
                .when(new UpdateSkyCommand(SKY_ID, "Orion-2"))
                .expectEvents(new SkyUpdatedEvent(SKY_ID, "Orion-2"));
    }

    @Test
    void updateSky_blankName_rejected() {
        fixture.given(new SkyCreatedEvent(SKY_ID, "Orion"))
                .when(new UpdateSkyCommand(SKY_ID, ""))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    void deleteSky_emitsDeletedEventAndMarksDeleted() {
        fixture.given(new SkyCreatedEvent(SKY_ID, "Orion"))
                .when(new DeleteSkyCommand(SKY_ID))
                .expectEvents(new SkyDeletedEvent(SKY_ID))
                .expectMarkedDeleted();
    }
}
