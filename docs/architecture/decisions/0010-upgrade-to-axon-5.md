# 0010 — Upgrade to Axon Framework 5 (Entity Model)

- **Status:** Accepted
- **Date:** 2026-05-07
- **Deciders:** Project maintainer
- **Tags:** axon, upgrade, entity-model, breaking

## Context and problem statement

After [ADR-0008](0008-spring-boot-4-baseline.md) landed Spring Boot 4 / Java 25 / Hibernate 7 / Jackson 3, the integration test (`SkyEndToEndIT`) revealed that **Axon Framework 4.9.x's Spring Boot autoconfig does not wire under Boot 4**. Specifically, `JpaEventStoreAutoConfiguration` declares `@AutoConfiguration` (no ordering hint) + `@ConditionalOnBean(EntityManagerFactory.class)`. Boot 4's stricter autoconfig phase-ordering causes the condition to evaluate before Hibernate registers the EMF; the autoconfig silently skips itself; no `EventStore` bean is created; the aggregate-repository factory throws *"Default configuration requires the use of event sourcing"*.

This was empirically verified against Axon **4.9.2**, **4.9.3**, and **4.10.4 (artifacts 4.10.3)** — all three exhibit the same Boot-4 wiring gap. Maven Central confirms there's no published Axon 4.x patch with a fix; **Axon 5 is the only line that targets Spring Boot 4 cleanly**.

Axon 5 is a fundamental rewrite, not a drop-in upgrade. Bytecode scouting confirmed:
- `@Aggregate` Spring stereotype → **gone**
- `@AggregateIdentifier`, `@TargetAggregateIdentifier` → **gone**
- `AggregateLifecycle.apply(...)` → **gone**
- `EventCountSnapshotTriggerDefinition` → **gone**
- `@ProcessingGroup` → **gone**
- No `axon-legacy:5.x` or `axon-migration:5.x` BOMs published (no backward-compat bridge)
- BOM artifact id renamed: `axon-bom` → `axon-framework-bom`
- Spring Boot integration moved to its own groupId: `org.axonframework.extensions.spring`

Axon 5 ships a new **Entity Model**: aggregate state is a plain class annotated `@EventSourcedEntity` (or its Spring stereotype `@EventSourced`); commands are routed to entities via `@InjectEntity(idProperty = "...")` on external command-handler methods; events tagged with `@EventTag` on their id field flow to a stream identified by the entity's `tagKey`.

## Decision drivers

- Boot 4 fully wired end-to-end, integration test green.
- No Axon-Server licensing — preserve the JPA event store on Postgres (ADR-0003).
- Hexagonal module rule preserved (ADR-0001) — the domain-only-no-Spring intent stays modulo the small annotations the framework requires.
- Snapshots are demo-grade (template threshold = 5); deferring them rather than rewriting to Axon 5's `SnapshotPolicy` API in this change is acceptable.
- The `/v1/starter/**` API surface and CQRS shape preserved.

## Considered options

1. **Manual Axon 4 wiring on Boot 4** — write our own `@Bean EventStore`, `@Bean EventStorageEngine`, etc. to bypass the broken autoconfig. ~50–80 LOC of Axon-internals plumbing, brittle, has to keep up with future Axon releases anyway.
2. **Wait for an Axon 4.x release with Boot 4 autoconfig fix** — would require AxonIQ to back-port; no such release announced; effectively waiting forever.
3. **Migrate to Axon 5 Entity Model.** Real rewrite, but supported, documented, and forward-aligned.

## Decision

**Option 3.** Full migration to Axon Framework 5.1.0 Entity Model.

### Concrete changes

| Area | Axon 4 (before) | Axon 5 (after) |
|---|---|---|
| BOM artifact | `org.axonframework:axon-bom` | **`org.axonframework:axon-framework-bom`** |
| BOM version | `4.9.3` | **`5.1.0`** |
| Spring Boot starter groupId | `org.axonframework` | **`org.axonframework.extensions.spring`** |
| Aggregate stereotype | `@Aggregate(snapshotTriggerDefinition = "...")` | **`@EventSourced(idType = UUID.class, tagKey = "skyId")`** (Spring-aware) |
| Aggregate id field | `@AggregateIdentifier` | (no annotation needed — id resolved via `@InjectEntity(idProperty)`) |
| Command id field | `@TargetAggregateIdentifier` | (no annotation needed — same name-match) |
| Event id field | (implicit) | **`@EventTag`** |
| Command handler location | Methods on the aggregate | **External `@Component` class**, `@CommandHandler` methods take `@InjectEntity SkyAggregate state` + `EventAppender` |
| Event emission | `AggregateLifecycle.apply(event)` | **`eventAppender.append(event)`** on the injected appender |
| Aggregate constructor | `@CommandHandler` constructor | **`@EntityCreator`** no-arg constructor |
| Annotation packages | `org.axonframework.{commandhandling,eventsourcing,eventhandling,queryhandling}` | **`org.axonframework.messaging.{commandhandling,eventhandling,queryhandling}.annotation`** + **`org.axonframework.eventsourcing.annotation`** |
| Gateway packages | `org.axonframework.commandhandling.gateway`, `org.axonframework.queryhandling` | **`org.axonframework.messaging.{commandhandling,queryhandling}.gateway`** |
| `CommandGateway.send(cmd)` | returns `CompletableFuture<R>` | returns `CommandResult`; for `CompletableFuture<R>` use `send(cmd, Class<R>)` |
| `@ProcessingGroup` on projection | required | **gone** — processor naming is configuration-only (`axon.eventhandling.processors.<name>`) |
| Processor mode | `tracking` | **`pooled`** (`SUBSCRIBING` and `POOLED` are the only modes) |
| Snapshot trigger | `EventCountSnapshotTriggerDefinition` + `Snapshotter` | **`SnapshotPolicy` + `SnapshotStore`** — *not wired in this change*; reintroduction tracked as a follow-up |
| JPA event-store schema | `domain_event_entry` + `snapshot_event_entry` + `token_entry` (no mask) + `saga_entry` + `association_value_entry` + `dead_letter_entry` | **`aggregate_event_entry`** + reshaped `token_entry` (with `mask` column) + sequence `aggregate-event-global-index-sequence`; old tables dropped |

### Concrete code-level adjustments (collected from the apply phase)

- **`AxonConfig`** — gutted; the snapshot-trigger bean is removed (Axon 5's `SnapshotPolicy`/`SnapshotStore` API is left for a follow-up).
- **`AggregateBasedJpaEventStorageEngine` Boot 4 autoconfig** wires automatically; the Axon-Boot4 wiring bug from 4.x is gone.
- **`@EventSourced`** (Spring stereotype from `org.axonframework.extension.spring.stereotype`) is required, not the raw `@EventSourcedEntity` — the Spring lookup (`SpringEventSourcedEntityLookup`) reads `idType`/`tagKey` attributes from the stereotype to register `Repository<SkyAggregate, UUID>`.
- **MongoDB UUID encoding**: Boot 4 deprecated `spring.data.mongodb.uuid-representation`; replaced with `spring.mongodb.representation.uuid: standard`.
- **Spring Security 7 in tests**: `@WithMockUser`'s context propagation through async dispatch is no longer reliable; the IT uses `with(user(...).authorities(ROLE_USER)).with(csrf())` MockMvc post-processors per request, which works with both filters enabled and method security.
- **`QueryExecutionException`** wraps query-handler exceptions; `GlobalExceptionHandler` gained an unwrapper that routes the cause to the appropriate handler (404 for `SkyNotFoundException`).
- **`SkyAggregateTest`** — Axon 5's `axon-test` 5.x replaced the classic `AggregateTestFixture` with `AxonTestFixture` (requires a full `ApplicationConfigurer`). The aggregate test is rewritten with Mockito (mock `EventAppender`, call methods directly, verify) — same invariants, lighter setup, framework-decoupled.
- **Liquibase `0002-axon-5-event-store-migration.yaml`** — drops the Axon 4 tables, creates Axon 5's `aggregate_event_entry` + sequence, recreates `token_entry` with the new `mask` column. Runs after `0001-axon-event-store-baseline` (per ADR-0009 the baseline is never edited).

### Deviation from ADR-0001 (hexagonal — no Spring in domain)

Axon 5's Entity Model needs the entity to be a Spring bean (the lookup uses `BeanFactory#getBeanNamesForAnnotation`). The pragmatic path is to add `@EventSourced` from `org.axonframework.extension.spring.stereotype` to `SkyAggregate`. This pulls a small `compileOnly` Spring dependency into the `domain` module — narrow, annotation-only, no runtime classes. Documented as a deliberate framework constraint.

## Consequences

### Positive

- `:app:test` is **green end-to-end** — first time since the Boot 4 upgrade.
- Helm / k8s deployment is unblocked.
- Axon 5's async-first messaging model is now in place; future feature work can use `MessageStream`, async command results, etc.
- The migration commit captures every change one place; future forks adopting Axon 5 have a working reference.

### Negative / accepted trade-offs

- **Snapshots are not wired**. The `axon.snapshot.*` properties were removed; `AxonConfig` is empty. Reintroduction follows once Axon 5's `SnapshotPolicy` + `SnapshotStore` setup is fully understood.
- **Dev databases lose data on first migration** — `0002-...yaml` drops Axon 4 tables. Acceptable for a template.
- **Domain has a `compileOnly` dep on Spring** for `@EventSourced`. Documented deviation from ADR-0001 — narrow and annotation-scoped.
- **Test count adjustment** in `SkyAggregateTest`: 7 → 7 (same count, different style). The Axon-test fixture rewrite is a Mockito-based rewrite of the same invariants; total test count across the project is **49** (was 43 pre-migration; +6 from the Mockito rewrite covering both command emission and event-sourcing handler state mutations).
- **`AxonTestFixture` not adopted** — would require an `ApplicationConfigurer` per test, much heavier. The tests we have cover the same surface with simpler tooling.
- **The 4.x `@TargetEntityId` / `@RoutingKey` exploration was wasted** — Axon 5 actually doesn't need these on commands; routing is by name match through `@InjectEntity(idProperty)`. Documented here so future readers don't make the same mistake.

## Links

- [ADR-0001 — Hexagonal module layout](0001-hexagonal-module-layout.md) (deviation noted above)
- [ADR-0002 — CQRS with Axon Framework](0002-cqrs-with-axon.md) (still holds; just on Axon 5)
- [ADR-0003 — Event sourcing on PostgreSQL](0003-event-sourcing-postgres-store.md) (still holds; new schema captured by ADR-0009 Liquibase changeset 0002)
- [ADR-0008 — Spring Boot 4 baseline + BOM-first version management](0008-spring-boot-4-baseline.md) (this change resolves the Axon-Boot4 incompatibility flagged there)
- [ADR-0009 — Liquibase + Axon event-store baseline](0009-liquibase-axon-baseline.md) (`0002-axon-5-event-store-migration.yaml` follows the precedent)
- OpenSpec change folder: `openspec/changes/upgrade-axon-5/` (proposal, design, specs/platform-baseline, tasks).
- [Axon 5 docs](https://docs.axoniq.io/axon-framework-5-getting-started/) — canonical reference for the Entity Model.
- [AxonIQ university-demo on GitHub](https://github.com/AxonIQ/university-demo/) — reference application.
