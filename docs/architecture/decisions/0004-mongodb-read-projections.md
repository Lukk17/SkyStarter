# 0004 — MongoDB for read projections

- **Status:** Accepted
- **Date:** 2025-11-15
- **Deciders:** Project maintainer
- **Tags:** projections, mongodb, cqrs

## Context and problem statement

Read views in a CQRS system are denormalised on purpose — they exist to serve specific query shapes cheaply. Reusing the relational event store for queries would either pollute it with read-shape tables or drag query latency through the event log.

We want a separate, query-optimised store for projections.

## Decision drivers

- Projections are denormalised JSON-ish documents.
- Adding a new query view should mean adding a new collection, not a new schema migration.
- Indexable on arbitrary fields without ALTER TABLE coordination.
- Mature Spring Data integration.

## Considered options

1. **MongoDB** as the projection store (separate datasource).
2. **PostgreSQL with `jsonb` columns** as the projection store.
3. **Elasticsearch** as the projection store.
4. **Single store**: also project into PostgreSQL relational tables.

## Decision

We chose **option 1**: a secondary `spring.data.mongodb` datasource backing `SkyMongoRepository`. The projection updates run inside the `sky-projection-processor` tracking event processor (`SkyProjection`). Projections are rebuildable by replaying the event store.

## Consequences

### Positive

- Independent scaling and indexing of the read model.
- Adding a new query shape is a new collection + new `@EventHandler` class.
- Document model maps cleanly to denormalised aggregate views.
- Failure-mode separation: a Mongo outage degrades reads but does not block writes.

### Negative / accepted trade-offs

- Two databases to operate.
- Eventual consistency window between write commit and projection update (typically sub-second; tested with Awaitility).
- Cross-datasource transactions are out — per-projection idempotency is the contract.
- `PersistenceConfiguration` needs the JPA-scan workaround to avoid Spring Data's "strict repository mode" picking up Mongo repos via JPA.

## Links

- [arc42 §6.3](../arc42/arc42.md#63-eventual-consistency-note)
- [diagram: container view](../diagrams/02-container-view.md)
