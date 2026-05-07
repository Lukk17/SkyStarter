## Context

After `upgrade-spring-boot-4` and `replace-ddlauto-with-liquibase`, the working tree is on Spring Boot 4.0.6 / Java 25 / Hibernate 7 / Jackson 3 / Liquibase 5.0.2, with Axon Framework still at 4.9.x. The integration test (`SkyEndToEndIT`) confirms the Axon ↔ Boot 4 incompatibility:

- `JpaEventStoreAutoConfiguration` is annotated `@AutoConfiguration` (no ordering hint) + `@ConditionalOnBean(EntityManagerFactory.class)`. Boot 4's stricter autoconfig phase-ordering causes the condition to evaluate before Hibernate has registered the EMF; the autoconfig silently skips itself; no `EventStore` bean is created.
- Verified empirically against Axon 4.9.2, 4.9.3, and 4.10.4. The bug is on the entire 4.x line. No 4.x patch addresses it.
- Axon 5 is recompiled against Boot 4 / Spring Framework 7 and resolves the ordering. It is the only path forward.

A naive bump to `axon-framework-bom 5.1.0` produces 21 compile errors in `domain/` alone. Bytecode scouting of all 5.1.0 jars shows that Axon 5 is a **fundamental rewrite**:

- The `@Aggregate` Spring stereotype, `@AggregateIdentifier`, `@TargetAggregateIdentifier`, `AggregateLifecycle.apply()`, `EventCountSnapshotTriggerDefinition` — none exist anywhere in Axon 5.
- No `axon-legacy:5.x` or `axon-migration:5.x` BOMs are published.
- New: an Entity Model under `org.axonframework.modelling.entity.*` with `@RoutingKey` annotations on commands; a `SnapshotPolicy` + `SnapshotStore` snapshot abstraction; annotation packages reorganised under `org.axonframework.messaging.{commandhandling,eventhandling,queryhandling}.annotation` and `org.axonframework.eventsourcing.annotation`; `AggregateBasedJpaEventStorageEngine` for the JPA event store with a new `AggregateEventEntry` table layout.

The constraints from earlier ADRs remain:
- Hexagonal module boundary holds (ADR-0001).
- Public `/v1/starter/**` API surface unchanged.
- Liquibase + the existing `0001-axon-event-store-baseline.{yaml,sql}` are not edited; any Axon 5 schema diff goes into a new `0002-axon-5-event-store-migration.yaml` (ADR-0009).
- Public domain ports (`SkyCommandService`, `SkyQueryService`) keep their `CompletableFuture` signatures — Axon 5's reactive types do not leak across the boundary.

## Goals / Non-Goals

**Goals:**
- Make `:app:test` green end-to-end on Axon 5.1.0 + Spring Boot 4.0.6 + Java 25.
- Migrate `SkyAggregate` to Axon 5's Entity Model with `@RoutingKey`-routed commands and the new event-emission idiom (handler returns or `EventAppender`-style — exact mechanism resolved at compile time).
- Migrate `SkyCommandServicePrimary` and `SkyQueryServicePrimary` to the Axon 5 gateway packages, preserving their `CompletableFuture<...>` public signatures.
- Migrate `SkyProjection` annotations + handler signatures to Axon 5.
- Replace `EventCountSnapshotTriggerDefinition` with a `SnapshotPolicy` + `SnapshotStore` setup, OR drop snapshots cleanly in this change with a documented follow-up.
- Author a Liquibase `0002-axon-5-event-store-migration.yaml` capturing the Axon 4 → 5 schema diff (only if `validate` complains; otherwise no file).
- Rewrite `SkyAggregateTest` to whatever fixture API `axon-test:5.x` exposes. The seven test cases keep their invariants.
- Add ADR-0010 + arc42 §2/§5.2/§9/§11 updates.

**Non-Goals:**
- No new functional behaviour on `/v1/starter/**`.
- No move to Axon 5's Dynamic Consistency Boundary (DCB) model. Aggregate-id-routed model preserved.
- No Axon Server adoption.
- No exposure of Axon 5's `MessageStream` or `Mono`-style types across the public ports.
- No reuse of dead-letter queue / saga / deadline features in this change. If Axon 5 changed those entities' tables and Hibernate `validate` complains, the `0002-...yaml` covers them; we don't wire them.

## Decisions

### D1. BOM coordinate change is two one-liners

The `axon-bom` Gradle alias keeps its name; only its `module` field flips: `org.axonframework:axon-bom` → `org.axonframework:axon-framework-bom`. Same for `axon-spring-boot-starter`: `org.axonframework:axon-spring-boot-starter` → `org.axonframework.extensions.spring:axon-spring-boot-starter`. Every `build.gradle.kts` that references these aliases stays untouched.

**Alternatives considered:** rename the aliases too (`axon-framework-bom`, `axon-extensions-spring-boot-starter`). Rejected — every `build.gradle.kts` would change for no gain.

### D2. Entity Model migration order: aggregate first, gateways second

Axon 5's Entity Model centres on the aggregate class itself. Once `SkyAggregate` is correctly annotated for the new model, the gateway and projection adaptations fall out naturally because their handler methods bind to the aggregate's command/event types.

Implementation order:
1. **Catalog change** (D1).
2. **`domain/` rewrite** — `SkyAggregate`, the three commands, the three events, the query. Make `:domain:compileJava` green.
3. **`service/` adaptations** — gateway imports, possible return-type wrapping. Make `:service:compileJava` green.
4. **`infrastructure/` adaptations** — `SkyProjection`, `AxonConfig`, `PersistenceConfiguration`. Make `:infrastructure:compileJava` green.
5. **Tests** — `SkyAggregateTest` first (canary for the fixture API change); then service and infrastructure tests; targeted, not bulk.
6. **Integration test** — iterate on Hibernate `validate` errors, Spring property warnings, bean wiring.

This order surfaces the largest semantic breaks first; if Axon 5's Entity Model fundamentally doesn't fit our domain, we discover it on day one rather than after fixing 50 other lines.

### D3. Aggregate model: classic Entity Model, not DCB

Axon 5 supports two aggregate styles:
- **Entity Model** (`org.axonframework.modelling.entity.*`) — closest to the classic aggregate-with-id model. Commands route to a specific entity instance via `@RoutingKey`; events are appended to a stream keyed by that entity's id. State is reconstituted by replaying events.
- **Dynamic Consistency Boundary (DCB)** — a relational/SQL-style model where commands declare their own consistency window without requiring an aggregate identifier.

We use the **Entity Model**. The `Sky` domain has a clear identifier (`skyId`); the existing event stream is already partitioned by it. DCB would force a domain rewrite that we explicitly disallow as a non-goal.

**Alternatives considered:** DCB. Rejected — out of scope.

### D4. Event-emission idiom resolved at compile time, not by guessing

Axon 5 replaces `AggregateLifecycle.apply(event)` with a different mechanism. Two known patterns:
- **Return events from command handlers**: `@CommandHandler Sky handle(CreateSkyCommand cmd) { return new SkyCreatedEvent(...); }` (or list/stream-of-events).
- **Inject an `EventAppender`/`EventPublisher`**: handler receives an appender as a parameter and calls `appender.append(event)`.

Which one Axon 5.1.0 uses (and whether both work) is resolved at compile time by reading the `@CommandHandler` annotation javadoc and the entity-model handler signatures. The migration sticks with whichever pattern produces the smallest diff against the existing `SkyAggregate` semantics.

### D5. Snapshot policy: best-effort wire-up, otherwise drop

`EventCountSnapshotTriggerDefinition` is gone. The Axon 5 surface for snapshots:
- `org.axonframework.eventsourcing.snapshot.api.Snapshotter` — interface (the producer).
- `org.axonframework.eventsourcing.snapshot.api.SnapshotPolicy` — interface (when to snapshot).
- `org.axonframework.eventsourcing.snapshot.store.SnapshotStore` / `StoreBackedSnapshotter` — storage + the wiring class.
- `org.axonframework.eventsourcing.snapshot.inmemory.InMemorySnapshotStore` — dev-friendly default.

If a one-liner equivalent of "every N events" exists (e.g. `SnapshotPolicy.afterEveryN(int)`), `AxonConfig` is rewritten to expose a `@Bean SnapshotPolicy` + `@Bean SnapshotStore`. If the API requires a substantial wiring effort, snapshots are **dropped in this change** with a TODO + follow-up ticket; the template's threshold (5) was always a demo value, not a production tuning.

This decision is binary: if dropped, `axon.snapshot.trigger.threshold` property is removed, the bean is removed, the `@Aggregate(snapshotTriggerDefinition = "...")` reference (which doesn't exist in Axon 5 anyway) is gone. ADR-0010 records which branch was taken.

**Alternatives considered:** invest a follow-up's worth of time in this change to fully reproduce the threshold-trigger behaviour. Rejected — scope creep; snapshots are an optimisation, not a correctness requirement.

### D6. JPA event-store schema migration: empirical, not speculative

We **don't** try to predict Axon 5's table layout from documentation. The verification gate is:

1. After the import migration compiles, run `:app:test`.
2. Read every `Schema validation: missing/extra/wrong-type ...` error from Hibernate.
3. For each error, add an explicit operation in `0002-axon-5-event-store-migration.yaml`: `addColumn`, `dropColumn`, `modifyDataType`, `createTable`, `dropTable`, `createSequence`.
4. Re-run; iterate.

The new changeset stacks on top of `0001-axon-event-store-baseline.sql`. On a fresh DB Liquibase applies both in order. On a legacy dev DB the baseline is MARK_RAN'd and only `0002-...yaml` runs. The result is byte-identical Axon 5 schema in both cases.

If the diff turns out to be substantial — e.g. Axon 5 dropped `domain_event_entry` entirely in favour of `aggregate_event_entry` — the changeset performs the rename + data move. Drop tables Axon 5 doesn't use (e.g. `dead_letter_entry` if Axon 5 reorganised the dead-letter queue) only if validation actually demands it; otherwise leave them as orphan inert tables (cheaper than risking a destructive drop on someone's dev data).

**Alternatives considered:** regenerate `0001-...sql` against Axon 5 from scratch. Rejected — violates ADR-0009's "baseline is a frozen artefact" principle.

### D7. Configuration property migration via spring-boot-properties-migrator (one-cycle dep)

Axon 5 may rename or remove keys under `axon.*`. To avoid silently-ignored properties:

1. Add `developmentOnly("org.springframework.boot:spring-boot-properties-migrator")` to `app/build.gradle.kts` for the duration of this task.
2. Run the application; the migrator logs every renamed key with its replacement.
3. Update `application.yaml` and `application-test.yaml` accordingly.
4. **Remove the migrator dependency before final commit.**

If the migrator catches nothing, the dep was harmless. If it catches keys, we've automated what would otherwise be tribal knowledge.

### D8. AggregateTestFixture: rewrite tests, don't preserve API parity

`SkyAggregateTest` uses `org.axonframework.test.aggregate.AggregateTestFixture` from `axon-test:4.9.x`. Axon 5's `axon-test:5.1.0` may have:
- (a) Kept the fixture API stable → trivial recompile.
- (b) Renamed/relocated the fixture class → import fix + adapt assertions.
- (c) Replaced the fixture entirely with a new test DSL → seven test cases rewritten.

We accept whichever reality presents. The seven test cases preserve their **invariants** (create emits `SkyCreatedEvent`; blank-name rejected; etc.), not their syntax. If branch (c) requires full rewrite, the cases are renamed and re-expressed — count stays at 7 in `SkyAggregateTest`.

**Alternatives considered:** delete `SkyAggregateTest` and rely on `SkyEndToEndIT` for aggregate coverage. Rejected — the integration test cannot exercise validation rejection paths cleanly (commands fail with stack traces from the gateway, not assertions in the aggregate). Aggregate-level unit tests stay.

### D9. `PersistenceConfiguration` `@EntityScan` packages

The current `@EntityScan(basePackages = {"com.lukksarna.skystarter.infrastructure.persistence", "org.axonframework"})` was correct for Axon 4 (it scanned all of Axon's `*.jpa` packages). Axon 5 may have moved its JPA entities into more specific packages (e.g. `org.axonframework.eventsourcing.eventstore.jpa`); a broader `org.axonframework` scan still finds them but pulls in noise. We narrow if the IT runtime complains; otherwise leave the broad scan as-is for the 5.x line.

### D10. ADR-0010 + arc42 + cleanup

- New ADR `docs/architecture/decisions/0010-upgrade-to-axon-5.md` covering: the Boot-4 wiring bug (with the specific autoconfig-ordering finding), why a 4.x patch wasn't an option, the Entity Model rewrite scope, the Snapshot Policy decision (D5 branch chosen), the schema migration outcome (D6 outcome), the test-fixture decision (D8 outcome).
- arc42 §2 — Axon row updated.
- arc42 §5.2 (Domain whitebox) — aggregate description rewritten.
- arc42 §9 — ADR-0010 link.
- arc42 §11 — Axon-Boot4 risk row removed; if snapshots were dropped, a "snapshots not currently wired under Axon 5" risk row added.
- README — no change expected.

## Risks / Trade-offs

- **Hidden Axon 5 API mismatches.** We're flying without comprehensive Axon 5 docs in this environment; some package paths and APIs are inferred from bytecode listings. Expect 2–3 iterations of compile-error cleanup beyond the planned 21 imports. Mitigation: module-by-module migration order (D2) localises errors; we can pause and re-design at any module gate.
- **Entity Model fundamental fit.** If Axon 5's Entity Model has a constraint we can't satisfy with the `Sky` domain (e.g. requires multi-entity aggregates we don't have), we fall back to a custom `Repository<SkyAggregate>` implementation rather than the Entity Model — a documented escape hatch.
- **Snapshots dropped** (D5 branch (b)). The template loses snapshot-trigger demo functionality. Acceptable, documented in ADR-0010.
- **Dev-data destruction in `0002-...yaml`.** If the Axon 5 schema migration drops or renames tables, dev databases lose data. Acceptable for a template; production has no template-managed Axon data.
- **`MessageStream` adaptation cost.** If `CommandGateway.send` returns `MessageStream<?>` instead of `CompletableFuture<UUID>`, the service primary classes need a `.first().asMono().toFuture()`-style adapter. Bounded — small classes, simple adapter.
- **Test count regression.** If branch D8(c) forces test rewrites, individual cases may merge or split. We keep the count at 7 within `SkyAggregateTest` (43 total) by preserving the invariants rather than the test method names.
- **`spring-boot-properties-migrator` left in by mistake.** Mitigation: explicit removal step in tasks.md §6 and verified in §9.

## Migration Plan

The plan is staged. Each gate is a green build (or a green specific test).

**Gate 1 — Catalog resolves:**
1. Update `axon-bom` and `axon-spring-boot-starter` library `module` values + `axonBom` version (D1).
2. `./gradlew :infrastructure:dependencies` shows `axon-framework-bom:5.1.0` and the starter at `org.axonframework.extensions.spring:axon-spring-boot-starter:5.1.0`.

**Gate 2 — `domain/` compiles:**
3. Rewrite `SkyAggregate` for Axon 5 Entity Model. Handler signatures resolved at compile time.
4. Re-annotate commands: `@TargetAggregateIdentifier` → `@RoutingKey`.
5. Update event imports if any moved (likely none — events are POJOs).
6. `./gradlew :domain:compileJava` green.

**Gate 3 — `service/` compiles:**
7. Update `CommandGateway` / `QueryGateway` imports.
8. If gateway return types changed, wrap them so the public `SkyCommandService` / `SkyQueryService` ports still return `CompletableFuture`.
9. `./gradlew :service:compileJava` green.

**Gate 4 — `infrastructure/` compiles:**
10. Update `@EventHandler`, `@QueryHandler`, `@ProcessingGroup` imports in `SkyProjection`.
11. Rewrite `AxonConfig` per D5 branch (a) wire `SnapshotPolicy` + `SnapshotStore`, or (b) drop snapshots.
12. Verify `PersistenceConfiguration` `@EntityScan` packages (D9).
13. `./gradlew :infrastructure:compileJava` green.

**Gate 5 — Unit tests:**
14. Adapt `SkyAggregateTest` to the Axon 5 fixture API (D8). Tests count stays 7.
15. Adapt `SkyCommandServicePrimaryTest`, `SkyQueryServicePrimaryTest` to new gateway signatures.
16. `./gradlew :domain:test :service:test :infrastructure:test` — 43 unit tests green.

**Gate 6 — Integration test green:**
17. Run `./gradlew :app:test` (Docker required).
18. Hibernate `validate` errors → add operations to a new `0002-axon-5-event-store-migration.yaml` (D6); update `db.changelog-master.yaml` include list.
19. Spring property warnings → add `spring-boot-properties-migrator` (D7), follow its hints, update YAML, remove the dep.
20. Bean-wiring errors → adjust `AxonConfig` per D5.
21. Iterate until all 4 IT cases pass.

**Gate 7 — Documentation + verification:**
22. Write ADR-0010 (D10).
23. Update arc42 §2, §5.2, §9, §11.
24. `./gradlew clean build` green.
25. `./gradlew dependencyCheckAnalyze` — no new high-severity findings vs. pre-Axon-5 baseline.
26. Audit `gradle/libs.versions.toml` against the `platform-baseline` spec.

Rollback: revert the upgrade commit. The `axon-bom` line reverts atomically; the `0002-axon-5-event-store-migration.yaml` file is removed (or its entry in `db.changelog-master.yaml` is reverted, which makes Liquibase re-baseline next start). Dev DBs created during the upgrade may need a manual drop+recreate.

## Open Questions

These are answered during apply, not before:

- **Does Axon 5's `@CommandHandler` on an entity method return events or use an injected appender?** Resolved at gate 2.
- **Is `AggregateTestFixture` still in `axon-test:5.x`?** Resolved at gate 5.
- **Does Axon 5's `JpaEventStoreAutoConfiguration` need any property to opt-in (e.g. `axon.eventstore=jpa`)?** Resolved at gate 6 by checking property-binder warnings.
- **What's the exact `SnapshotPolicy` factory method for "every N events"?** Resolved at gate 4. If no such method exists, D5 branch (b) is taken.
- **Does the `0002-...yaml` need to drop the Axon 4 `dead_letter_entry`, `saga_entry`, `association_value_entry` tables?** Resolved empirically at gate 6 — only what Hibernate `validate` complains about.
- **Does `axon-spring` (the new groupId artifact) bring its own autoconfig classes, or does the starter cover everything?** Read the starter POM transitive list at gate 1; resolve at gate 4 if `infrastructure/` needs an explicit `axon-spring` import.
