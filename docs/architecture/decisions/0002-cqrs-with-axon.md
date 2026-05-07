# 0002 — CQRS with Axon Framework

- **Status:** Accepted
- **Date:** 2025-11-15
- **Deciders:** Project maintainer
- **Tags:** cqrs, axon, framework

## Context and problem statement

The template targets services where reads and writes have different shapes, different scaling envelopes, and different audit needs — the classic case for CQRS. Implementing CQRS by hand on top of Spring is doable but tedious: command bus, query bus, idempotency, gateway abstraction, snapshot triggers, and projection plumbing all need to be reinvented.

We need a CQRS framework that is mature, JVM-native, and works without licensed infrastructure.

## Decision drivers

- Mature, stable CQRS abstractions (CommandGateway, QueryGateway, EventBus).
- First-class event sourcing support (aggregate replay, snapshots).
- No mandatory licensed components.
- Spring Boot starter integration.

## Considered options

1. **Hand-rolled CQRS** on top of Spring `ApplicationEventPublisher`.
2. **Axon Framework** without Axon Server (PostgreSQL event store).
3. **Eventuate Tram / Eventuate ES**.

## Decision

We chose **option 2**: Axon Framework with `axon.axonserver.enabled=false` and the JPA event store on the primary PostgreSQL datasource.

Key wiring lives in `infrastructure/.../config/persistence/AxonConfig` (snapshot trigger) and `application.yaml` (event processor segmentation, Jackson serialiser).

## Consequences

### Positive

- Out-of-the-box `CommandGateway`, `QueryGateway`, `@Aggregate`, `@CommandHandler`, `@EventSourcingHandler`, `@QueryHandler`.
- Snapshotting via `EventCountSnapshotTriggerDefinition` configurable per env.
- Tracking event processors with segmented parallelism for free.
- `axon-test` provides a fixture-based testing API for aggregates that's independent of Spring.

### Negative / accepted trade-offs

- Axon API leaks into the domain (`@Aggregate`, `@CommandHandler` annotations on `SkyAggregate`). We accept this — the alternative is an awkward translation layer.
- Without Axon Server, distributed multi-instance command routing requires care (segments help, but cross-instance command de-duplication is the application's problem if it needs it).
- Framework upgrades have to be coordinated with Spring Boot upgrades (Axon 4.9 ↔ Spring Boot 3.x compatibility).

## Links

- [arc42 §4](../arc42/arc42.md#4-solution-strategy), [§5.2](../arc42/arc42.md#52-domain-level-2)
- [ADR-0006](0006-tracking-processor-segmentation.md)
