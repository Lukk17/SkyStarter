# 0012 — Read-model caching is deferred (the projection is the read cache)

- **Status:** Accepted
- **Date:** 2026-05-27
- **Deciders:** Project maintainer
- **Tags:** cqrs, caching, redis, scope, deferred

## Context and problem statement

The question came up of adding Redis as a read cache to this template. In a
CQRS + Event Sourcing system the read side is already a denormalised,
read-optimised **MongoDB projection** (ADR-0004) — its entire purpose is to
serve reads cheaply. A Redis cache would therefore sit *in front of an already
read-optimised store*: a cache on a cache.

SkyStarter is a reference / starter template. Its value is a focused,
idiomatic demonstration of CQRS + ES on Axon 5, not a kitchen-sink of every
production add-on. So the real question is not "is caching useful?" (sometimes
it is) but "does a Redis cache belong in the *core template*?".

## Decision drivers

- Keep the template a focused CQRS + ES reference; every add-on is also noise.
- Avoid teaching premature optimisation — caching an already-optimised read
  model by default is a questionable default reflex.
- Minimise moving parts: a third datastore (Postgres + Mongo + Redis) is more
  for a newcomer to stand up, understand, and keep current.
- Stay consistent with the template's existing minimalism (no Spring Modulith,
  no custom Axon metrics, snapshots deferred).
- Still capture the pattern so a reader who genuinely needs it isn't left
  guessing — including the one real implementation gotcha.

## Considered options

1. **Add a Redis read-through cache to the core template.** Demonstrates the
   pattern in running code, but adds a third datastore and shifts the
   template's lesson from "CQRS + ES" toward "caching".
2. **Do not add it; document when/how/why-not (chosen).** Keeps the core at two
   datastores and records the decision and the recipe.
3. **Optional branch or module.** Branches rot and modules add structure for a
   feature nobody may use; an ADR does not rot.

## Decision

We chose **Option 2**. Redis stays out of the core template. This document
records when a read cache is actually warranted and how to add it correctly, so
the decision is reversible without re-deriving it.

**When a read cache actually earns its place** (decide per-service, after
measuring — not by default):

- A few very hot keys read far more than the rest.
- A sub-millisecond read SLA a Mongo point lookup won't meet.
- A cache shared across many instances rather than each hitting Mongo.
- Shielding the projection store from read spikes.

**How to add it, when you do.** Do **not** put `@Cacheable` / `@CacheEvict`
directly on the Axon-invoked `@QueryHandler` / `@EventHandler` methods: Axon
invokes those itself and the call may bypass Spring's caching proxy, so the
annotations silently do nothing. Instead, delegate to a separate Spring
`@Component` that owns the read and is reliably proxied:

```java
@Component
class SkyReadModelStore {
    @Cacheable("sky-read")
    Sky findById(UUID id) { /* read Mongo + map */ }

    @CacheEvict("sky-read")
    void evict(UUID id) { }
}
```

`SkyProjection`'s query handler calls `store.findById(id)`; its
`on(SkyUpdatedEvent)` / `on(SkyDeletedEvent)` handlers call `store.evict(id)`.
The lesson this encodes: **the same domain events that update the read model
invalidate its cache.** Add `spring-boot-starter-data-redis` +
`spring-boot-starter-cache` in `infrastructure`, `@EnableCaching`, and Redis to
the compose stack; prove it with one Testcontainers integration test
(hit → evict-on-event → miss).

## Consequences

### Positive

- The template stays a focused two-datastore CQRS + ES reference.
- The caching pattern — and the proxy-boundary gotcha — are captured for anyone
  who needs them, without shipping unused infrastructure.

### Negative / accepted trade-offs

- A service that later needs the cache implements it then (the recipe above is
  the starting point).
- Caching widens the existing eventual-consistency window and adds invalidation
  edges; that subtlety is the reason the recipe ties eviction to the events.

## Links

- [ADR-0002](0002-cqrs-with-axon.md) — CQRS with Axon.
- [ADR-0004](0004-mongodb-read-projections.md) — MongoDB read projections (the
  read model this would cache).
- [ADR-0013](0013-native-image-fast-boot-deferred.md) — the sibling
  "deferred add-on" decision for native image / fast boot.
