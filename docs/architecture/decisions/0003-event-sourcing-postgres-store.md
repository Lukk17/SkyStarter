# 0003 — Event sourcing on PostgreSQL

- **Status:** Accepted
- **Date:** 2025-11-15
- **Deciders:** Project maintainer
- **Tags:** event-sourcing, postgres, persistence

## Context and problem statement

CQRS in this template is paired with event sourcing: aggregate state is derived from a stream of immutable events. We need a durable event store. The platform's operational baseline is PostgreSQL — every team already runs one. Introducing a dedicated event store (EventStoreDB, Axon Server, Kafka-as-store) would add a new operational surface for every adopting team.

## Decision drivers

- Reuse existing operational expertise (PostgreSQL).
- No licensed infrastructure.
- Snapshots and tracking processors must be supported.
- Event payloads must remain compact and queryable by Axon's event store schema.

## Considered options

1. **Axon JPA event store on PostgreSQL** (the primary `spring.datasource`).
2. **Axon Server** (free dev edition, paid for HA).
3. **EventStoreDB** as a dedicated event store.
4. **Kafka with log compaction** as an event "store".

## Decision

We chose **option 1**: Axon's JPA event store on the primary PostgreSQL datasource. Event payloads are serialised with Jackson (`axon.serializer.events: jackson`) and stored as `bytea` via the custom `ByteaEnforcedPostgresSQLDialect`.

## Consequences

### Positive

- One database technology to operate.
- Standard PostgreSQL backup/restore covers the event log.
- `bytea` payloads are inspectable with `convert_from(payload, 'UTF8')` for debugging.
- Snapshots fit naturally in the same store.

### Negative / accepted trade-offs

- The event log lives in the same physical database as the JPA aggregate roots — long-term, this couples write-availability of the API to the same Postgres instance.
- High event volumes will eventually need partitioning or a real event store. Re-evaluate at ~10⁸ events.
- The custom dialect is a small piece of glue that has to keep up with Hibernate releases.

## Links

- [arc42 §3.2](../arc42/arc42.md#32-technical-context), [§5.3](../arc42/arc42.md#53-infrastructure-level-2)
- [ADR-0007](0007-no-liquibase.md)
