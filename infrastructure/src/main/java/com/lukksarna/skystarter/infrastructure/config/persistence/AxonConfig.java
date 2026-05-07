package com.lukksarna.skystarter.infrastructure.config.persistence;

import org.springframework.context.annotation.Configuration;

/**
 * Axon Framework configuration.
 *
 * Axon 5 removed both {@code Snapshotter} (the producer interface) and
 * {@code EventCountSnapshotTriggerDefinition} (the threshold-based trigger).
 * Snapshots are now managed via the {@code SnapshotPolicy} + {@code SnapshotStore}
 * interfaces under {@code org.axonframework.eventsourcing.snapshot.*}.
 *
 * The threshold-trigger setup we had on Axon 4 has no one-line equivalent in
 * Axon 5; reintroducing snapshots is tracked as a follow-up (see ADR-0010).
 * The template's previous threshold of 5 was a demo value, never a production
 * tuning, so dropping it has no operational impact.
 */
@Configuration
public class AxonConfig {
}
