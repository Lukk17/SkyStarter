package com.lukksarna.skystarter.infrastructure.config.persistence;

import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.Snapshotter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AxonConfig {

    @Bean
    public EventCountSnapshotTriggerDefinition snapshotTriggerDefinition(
            Snapshotter snapshotter,
            @Value("${axon.snapshot.trigger.threshold:250}") int snapshotThreshold
    ) {
        return new EventCountSnapshotTriggerDefinition(snapshotter, snapshotThreshold);
    }
}
