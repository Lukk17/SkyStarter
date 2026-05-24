package com.lukksarna.skystarter.infrastructure.config.persistence;

import org.springframework.context.annotation.Configuration;

/**
 * Axon Framework configuration.
 *
 * Snapshots are intentionally not wired. Axon 5.1.0 ships the
 * {@code SnapshotPolicy} API but its {@code SnapshotStore} SPI is
 * {@code @Internal} and only reachable through the manual
 * {@code EventSourcedEntityModule.declarative(...)} builder, which is
 * incompatible with the {@code @EventSourced} auto-detection this template is
 * built around. Reintroducing snapshots is a tracked deferred item — see
 * ADR-0011 — until Axon publishes a stable, auto-detection-compatible store.
 */
@Configuration
public class AxonConfig {
}
