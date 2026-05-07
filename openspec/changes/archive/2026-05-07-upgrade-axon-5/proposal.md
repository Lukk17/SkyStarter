## Why

After `upgrade-spring-boot-4` and `replace-ddlauto-with-liquibase` landed, the working tree is on Spring Boot 4.0.6 / Java 25 / Hibernate 7 / Jackson 3 / Liquibase 5.0.2. The integration test (`SkyEndToEndIT`) exposes that Axon Framework 4.x's auto-configuration **does not wire under Spring Boot 4**: `JpaEventStoreAutoConfiguration` declares `@ConditionalOnBean(EntityManagerFactory.class)` but lacks an `@AutoConfigureAfter(HibernateJpaAutoConfiguration.class)` ordering hint, and Boot 4's stricter autoconfig phase-ordering causes the condition to evaluate before the EMF is registered. The condition fails silently; no `EventStore` bean is created; the aggregate-repository factory throws *"Default configuration requires the use of event sourcing"*.

This was verified empirically against Axon **4.9.2**, **4.9.3**, and **4.10.4 (artifact 4.10.3)** — all three exhibit the same Boot-4 wiring gap. Maven Central confirms there's no published Axon 4.x patch with a fix; **Axon 5 is the only line that targets Spring Boot 4 cleanly** (it ships under a renamed BOM `axon-framework-bom` and recompiles its autoconfig against Boot 4's APIs).

Axon 5 is **not a drop-in replacement** for Axon 4. Initial scouting confirmed:
- The classic aggregate stereotype `@Aggregate` is **gone** — no replacement under any package.
- `@AggregateIdentifier`, `@TargetAggregateIdentifier`, `AggregateLifecycle.apply()` — **gone**.
- `EventCountSnapshotTriggerDefinition` — **gone**, replaced by `SnapshotPolicy` + `SnapshotStore` interfaces.
- No `axon-legacy:5.x` or `axon-migration:5.x` BOMs are published — there is **no backward-compat bridge**.
- The Spring Boot integration moved to a new groupId (`org.axonframework.extensions.spring`).

What Axon 5 ships instead:
- An **Entity Model** (`org.axonframework.modelling.entity.*`) where commands route to entities via `@RoutingKey` annotations, replacing aggregate-identifier-based routing.
- Annotation packages reorganised under `org.axonframework.messaging.{commandhandling,eventhandling,queryhandling}.annotation` and `org.axonframework.eventsourcing.annotation`.
- A re-aligned `AggregateBasedJpaEventStorageEngine` that still persists events on PostgreSQL (the JPA event-store concept survived; the table layout may differ — verifiable only when Hibernate `validate` runs).

This change executes the full Axon 5 entity-model migration end to end. After it lands, `:app:test` is green and the `helm-k8s-deployment` change is unblocked.

## What Changes

### Build & artefact coordinates
- **BREAKING**: BOM rename `org.axonframework:axon-bom` → **`org.axonframework:axon-framework-bom`**, version `4.9.3` → **`5.1.0`**.
- **BREAKING**: Spring Boot starter groupId `org.axonframework` → **`org.axonframework.extensions.spring`** (artifact id `axon-spring-boot-starter` unchanged). Same for `axon-spring-boot-autoconfigure`, `axon-spring`, `axon-spring-boot-starter-test`.
- Catalog `axon-bom`, `axon-spring-boot-starter` library entries: `module` field updated; alias names unchanged so no `build.gradle.kts` import line changes.
- Core artifacts (`axon-modelling`, `axon-eventsourcing`, `axon-test`) keep their groupId (`org.axonframework`) per the new BOM.

### Domain model rewrite
- **BREAKING**: `SkyAggregate` rewritten to Axon 5's Entity Model. The class is no longer marked `@Aggregate`; commands carry `@RoutingKey` on the id field (replacing `@TargetAggregateIdentifier`); event-emission no longer uses the `AggregateLifecycle.apply(...)` static API but Axon 5's handler-return-or-message-stream pattern (exact API resolved at compile time from `org.axonframework.modelling.entity.*`).
- **BREAKING**: `CreateSkyCommand`, `UpdateSkyCommand`, `DeleteSkyCommand` — `@TargetAggregateIdentifier` → `@RoutingKey`. Same field, different annotation.
- Events (`SkyCreatedEvent`, `SkyUpdatedEvent`, `SkyDeletedEvent`) likely keep their POJO shape — events are still serialisable records of past facts. The Axon-side handler signatures change.
- Domain `SkyValidator` is unaffected (no Axon coupling).

### Service layer
- **BREAKING (imports)**: `CommandGateway` and `QueryGateway` move package: `org.axonframework.commandhandling.gateway.CommandGateway` → `org.axonframework.messaging.commandhandling.gateway.CommandGateway` (verified in 5.1.0 jar); `org.axonframework.queryhandling.QueryGateway` → `org.axonframework.messaging.queryhandling.gateway.QueryGateway`.
- **POSSIBLE**: gateway return types may have changed (Axon 5 introduces `MessageStream`). The public `SkyCommandService` / `SkyQueryService` ports keep returning `CompletableFuture<...>` — adapter code in the service primary classes converts if needed.

### Infrastructure layer
- **BREAKING (imports)**:
  - `org.axonframework.eventhandling.EventHandler` → `org.axonframework.messaging.eventhandling.annotation.EventHandler`.
  - `org.axonframework.queryhandling.QueryHandler` → `org.axonframework.messaging.queryhandling.annotation.QueryHandler`.
  - `org.axonframework.config.ProcessingGroup` → relocation TBD (likely under `org.axonframework.eventhandling.processor` or similar; resolved at compile time).
- **BREAKING (`AxonConfig`)**: `EventCountSnapshotTriggerDefinition` and the classic `Snapshotter` interface no longer exist. Replace with Axon 5's `SnapshotPolicy` (e.g. `SnapshotPolicy.afterEveryNEvents(threshold)`) wired to a `SnapshotStore` (`InMemorySnapshotStore` for dev, `JpaSnapshotStore` or equivalent for prod). If the API shape doesn't fit our threshold-driven setup, snapshots are dropped in this change and re-added in a follow-up — the template's threshold (`5`) is a demo value anyway.
- **BREAKING**: the `@Aggregate(snapshotTriggerDefinition = "snapshotTriggerDefinition")` reference on `SkyAggregate` is rewritten with whatever the Axon 5 entity-model + snapshot-policy idiom looks like.
- **POSSIBLE**: `PersistenceConfiguration`'s `@EntityScan(basePackages = "org.axonframework")` may need a different package list. Axon 5's `AggregateEventEntry` is at `org.axonframework.eventsourcing.eventstore.jpa.AggregateEventEntry`; the scan target is updated per the new package layout.

### Event-store schema
- Axon 5 redesigned the JPA event store. `AggregateBasedJpaEventStorageEngine` exists; `AggregateEventEntry` is the new entity (vs. Axon 4's `DomainEventEntry` + `SnapshotEventEntry` + `TokenEntry` + `SagaEntry` + `AssociationValueEntry` + `DeadLetterEntry`). The Liquibase baseline `0001-axon-event-store-baseline.sql` was tuned to Axon 4.9 column layouts and will mismatch Axon 5.
- A new Liquibase changeset **`0002-axon-5-event-store-migration.yaml`** captures the diff. Per ADR-0009, the existing `0001-*` is never edited.
- The existing changelog include in `db.changelog-master.yaml` is amended with the second include line.
- Hibernate `validate` is the verification mechanism — every column it complains about becomes an `addColumn`/`dropColumn`/`createTable`/`dropTable` step in `0002-...yaml`.

### Configuration
- `axon.snapshot.trigger.threshold` (custom — defined by us) is removed if `AxonConfig` drops the snapshot-trigger bean; reintroduced under whatever key Axon 5 expects if a `SnapshotPolicy` bean replaces the trigger.
- `axon.eventhandling.processors.<name>.{mode,initial-segment-count}` keys verified against Axon 5 reference. Renamed if necessary; Spring Boot **properties-migrator** is added as a one-cycle developmentOnly dep to surface the rename automatically and is removed before final commit.
- `axon.axonserver.enabled: false`, `axon.serializer.events: jackson`, etc. — checked against the Axon 5 binding metadata.

### Tests
- `SkyAggregateTest` — Axon 5's `axon-test` 5.x has a different fixture API (the classic `AggregateTestFixture` may have been retained, replaced with a builder, or removed entirely; resolved at compile time). The seven test cases are rewritten to express the same invariants in whichever API exists.
- `SkyCommandServicePrimaryTest` / `SkyQueryServicePrimaryTest` — gateway type signatures updated.
- `StarterControllerTest`, `SkyProjectionTest`, `SkyMapperTest`, `KeycloakAuthenticationConverterTest`, `GlobalExceptionHandlerTest`, `SkyValidatorTest` — no expected change; verify they still pass.
- `SkyEndToEndIT` — should pass without test-code changes once the wiring layer is correct.

### CI
- The migration commit will trigger `verifyMigrationCoverage` because Axon 5's `@Entity` bytecode hashes differ from Axon 4's. Either the new `0002-axon-5-event-store-migration.yaml` is present (satisfies the guard) or the commit message includes `[no-migration]` (override marker, used only if Axon 5 happens to produce byte-identical entity classpath, which is unlikely).

### Documentation
- **NEW** ADR `docs/architecture/decisions/0010-upgrade-to-axon-5.md` covering: the autoconfig-ordering bug that motivated the move, why a 4.x patch wasn't an option, the entity-model rewrite scope, what we kept (CQRS + event sourcing + JPA event store + tracking processor + Liquibase), what we replaced (`@Aggregate` → entity model; `AggregateLifecycle.apply` → handler returns; `EventCountSnapshotTriggerDefinition` → `SnapshotPolicy`).
- arc42 §2 compatibility-matrix Axon row — version 5.1.0; BOM artifact id `axon-framework-bom`; starter groupId `org.axonframework.extensions.spring`.
- arc42 §5.2 (Domain whitebox) — aggregate description updated to describe entity model + `@RoutingKey`.
- arc42 §6 (Runtime view) — sequence diagrams stay; only annotation names change in the descriptions.
- arc42 §9 — ADR-0010 listed.
- arc42 §11 — Axon-Boot4-incompatibility risk row removed.
- README — no change expected.

### Non-goals
- No new endpoints, no API shape changes on `/v1/starter/**`.
- No move to Axon 5's Dynamic Consistency Boundary (DCB) model — we keep aggregate-id-style consistency.
- No Axon Server adoption.
- No exposure of Axon 5's reactive types (`MessageStream`) across the public ports — `CompletableFuture` remains the boundary.
- No swap of any database, IdP, or framework outside the Axon line.

## Capabilities

### New Capabilities

(None.)

### Modified Capabilities

- `platform-baseline` — two requirements modified:
  1. **`Aggregate framework alignment`** records Axon 5.1.0 + the BOM rename + the starter groupId move.
  2. **`Per-environment compatibility matrix`** updates the Axon row.

## Impact

- **Code (lines, rough estimate)**:
  - `domain/`: ~80–120 lines touched. `SkyAggregate` rewritten; commands re-annotated; events likely unchanged.
  - `service/`: ~10–20 lines. Gateway imports + (if needed) return-type adapters.
  - `infrastructure/`: ~30–50 lines. `SkyProjection` annotation imports; `AxonConfig` rewritten around `SnapshotPolicy`/`SnapshotStore` (or dropped); `PersistenceConfiguration` `@EntityScan` package list verified.
  - `app/src/main/resources/application.yaml`: a handful of property keys.
  - `infrastructure/src/main/resources/db/changelog/0002-axon-5-event-store-migration.yaml`: new file, expected ~50–100 lines depending on schema diff scope.
- **Tests**:
  - `SkyAggregateTest` rewritten to the Axon 5 fixture API.
  - `SkyCommandServicePrimaryTest`, `SkyQueryServicePrimaryTest` adapted to the gateway type changes.
  - 43 unit tests target stays the same; the test count must remain 43 unless an explicit ADR justification accompanies any deletion.
- **CI**:
  - `verifyMigrationCoverage` is satisfied by the `0002-...yaml` (or by the `[no-migration]` marker in the unlikely no-schema-change branch).
  - `dependencyCheckAnalyze` may surface new transitive CVEs given a substantial library change — review and either suppress (with justification) or escalate.
- **Runtime**:
  - First start against an existing dev DB: Liquibase finds `0001-...` already applied (MARK_RAN or EXECUTED), applies `0002-...`. The `0002-...` will rename/reshape Axon 4 tables to Axon 5 layout — destructive on dev data, acceptable for a template.
  - First start against a fresh DB: Liquibase applies both `0001-...` and `0002-...` in order. Axon 5 starts cleanly.
- **Docs**: ADR-0010 + arc42 §2/§5.2/§9/§11 + README (light).
- **Downstream changes unblocked**: `helm-k8s-deployment` proposal can proceed because the app context fully boots.

### Risk

The Axon 5 API surface is materially larger than what was assumed in the original draft of this proposal. Some sub-tasks (snapshot-policy wiring, event-store schema migration, fixture-API rewrite) may discover further mismatches at apply time. The mitigation is staged: implement module-by-module, keep build/tests green at each gate, and pause + revise the design rather than power through if a fundamental assumption breaks. Snapshots specifically may be dropped in this change with a follow-up to reintroduce them under Axon 5's `SnapshotPolicy` once we understand the new contract.
