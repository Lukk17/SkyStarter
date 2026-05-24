# 0011 — Defer aggregate snapshots on Axon 5

- **Status:** Accepted
- **Date:** 2026-05-24
- **Deciders:** Project maintainer
- **Tags:** axon, event-sourcing, snapshots, deferred

## Context and problem statement

[ADR-0010](0010-upgrade-to-axon-5.md) migrated the template to Axon Framework 5's
Entity Model and noted that the Axon 4 snapshot-trigger setup
(`EventCountSnapshotTriggerDefinition`) has no drop-in equivalent. In the interim
`application.yaml` still carried `axon.snapshot.trigger.threshold: 5` and the
startup banner logged "Snapshot every: 5 events" — but nothing read that property
and no snapshotter bean existed. The configuration was dead and the banner was
lying: every `SkyAggregate` was replayed from event one on each load.

This ADR records the decision on how to resolve that, after inspecting the Axon
5.1.0 sources to establish what snapshotting actually requires.

## What Axon 5.1.0 actually offers

- `SnapshotPolicy` (public) — e.g. `SnapshotPolicy.afterEvents(n)`.
- `SnapshotStore` and `StoreBackedSnapshotter` — both annotated
  `@org.axonframework.common.annotation.Internal`, i.e. explicitly unstable.
- The only shipped `SnapshotStore` implementation is `InMemorySnapshotStore`
  (non-durable, per-instance). There is no JPA/JDBC store for the PostgreSQL
  event store this template uses, and no `AxonServerSnapshotStore` on the
  classpath (we run the embedded JPA store, not Axon Server).
- A `SnapshotPolicy` can only be attached through the manual
  `EventSourcedEntityModule.declarative(...)` builder's `OptionalPhase`. The
  Spring Boot starter's `SpringEventSourcedEntityConfigurer` hardcodes
  `EventSourcedEntityModule.autodetected(...)`, which exposes **no** snapshot
  hook. There is no Spring seam to add a policy to an auto-detected entity.

## Decision drivers

- The template's defining pattern is `@EventSourced` auto-detection of the
  aggregate; keeping it is more valuable than a half-working optimisation.
- Production code should not be built on `@Internal` SPIs that can break on any
  Axon minor release.
- The codebase and docs must not claim a capability that is not active.

## Considered options

1. **Custom persistent `SnapshotStore`** — JPA-backed store + Liquibase table +
   `Position`/payload serialization, wired by hand-replicating
   `AnnotatedEventSourcedEntityModule` (dropping `@EventSourced`) to insert
   `.snapshotPolicy(afterEvents(n))`. Works today, but rests on `@Internal` APIs,
   abandons auto-detection, and re-implements framework internals.
2. **`InMemorySnapshotStore` + policy** — smaller, but still drops
   auto-detection and rides the same `@Internal` APIs, and snapshots vanish on
   restart.
3. **Defer cleanly.** Remove the dead config and the banner line, keep
   `@EventSourced`, and document snapshots as a tracked follow-up.

## Decision

We chose **Option 3**. Snapshots stay unwired until Axon ships a stable,
auto-detection-compatible snapshot store. Concretely:

- Removed `axon.snapshot.trigger.threshold` from `application.yaml`.
- Removed the "Snapshot every: N events" line from the startup banner
  (`StartupLogConfig`).
- `AxonConfig`'s Javadoc now states the deferral and points here.

## Consequences

### Positive

- No dead config and no misleading banner output; the code tells the truth.
- The `@EventSourced` auto-detection pattern — the point of the template — is
  preserved.
- No dependency on `@Internal` Axon APIs.

### Negative / accepted trade-offs

- Aggregates with very long event streams replay all events on load. This is a
  non-issue for the demo workload and acceptable for a starter; production users
  with long streams must revisit once snapshots are available.

## Links

- [ADR-0010](0010-upgrade-to-axon-5.md) — Axon 5 upgrade (where the deferral
  originated).
- [ADR-0003](0003-event-sourcing-postgres-store.md) — PostgreSQL event store.
- Reassess when Axon publishes a non-`@Internal`, auto-detection-compatible
  `SnapshotStore`.
