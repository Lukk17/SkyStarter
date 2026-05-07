## Why

The previous change (`upgrade-spring-boot-4`) brought us to Spring Boot 4.0.6 / Spring Framework 7 / Hibernate 7 / Jackson 3 — but kept Axon Framework at 4.9.x because at the time no Boot-4-compatible Axon line was confirmed. The integration test (`SkyEndToEndIT`) now reveals the cost of that compromise: Axon 4.9.2's auto-configuration does not wire under Boot 4. Specifically, `JpaEventStoreAutoConfiguration` does not fire, no `EventStore` bean is created, the aggregate-repository factory throws *"Default configuration requires the use of event sourcing"*, and the application context fails to load. The unit tests pass (43 green) and Liquibase + Hibernate `validate` succeed — the entire failure surface is at the Axon ↔ Spring Boot 4 seam.

Axon 5.1.0 GA exists on Maven Central with explicit Boot 4 / Hibernate 7 / Jackson 3 support. The artifact layout was reorganised: the BOM is renamed (`axon-bom` → `axon-framework-bom`), the Spring Boot integration moved to a new groupId (`org.axonframework.extensions.spring`), the messaging model was redesigned async-first, and several core annotation packages moved. A naive BOM bump produced 21 compile errors in the `domain` module alone — confirming this is a real migration, not a version flip.

This change finishes the platform-modernisation arc that started with Boot 4 + Liquibase. After it lands, `:app:test` is green end-to-end and the Helm / k8s deployment change is no longer blocked.

## What Changes

- **BREAKING (build)**: BOM artifact renamed `org.axonframework:axon-bom` → **`org.axonframework:axon-framework-bom`**. Version `4.9.3` → **`5.1.0`**.
- **BREAKING (build)**: Spring Boot starter groupId changed `org.axonframework` → **`org.axonframework.extensions.spring`** (artifact id `axon-spring-boot-starter` unchanged). The catalog entry's `module` coordinate is updated; everywhere we declare `axon-spring-boot-starter` the change is mechanical.
- **BREAKING (code, ~21 imports)**: Axon 5 moved several core annotations and gateways. Likely paths (verified during apply):
  - `org.axonframework.commandhandling.CommandHandler` → relocated.
  - `org.axonframework.eventsourcing.EventSourcingHandler` → relocated.
  - `org.axonframework.modelling.command.{TargetAggregateIdentifier,AggregateLifecycle}` → relocated.
  - `org.axonframework.spring.stereotype.Aggregate` → relocated.
  - `org.axonframework.commandhandling.gateway.CommandGateway`, `org.axonframework.queryhandling.QueryGateway` → relocated.
  - `org.axonframework.eventhandling.EventHandler`, `org.axonframework.queryhandling.QueryHandler` → relocated.
  - `org.axonframework.config.ProcessingGroup` → relocated.
  - `org.axonframework.eventsourcing.{EventCountSnapshotTriggerDefinition,Snapshotter}` → API likely changed.
- **BREAKING (semantics, expected)**: Axon 5's messaging model is async-first. `CommandGateway.send` and `QueryGateway.query` may now return `CompletableFuture` shapes that differ from 4.x; aggregate command handlers may now optionally return values asynchronously. Our `SkyCommandServicePrimary` and `SkyQueryServicePrimary` already use `CompletableFuture` so the public ports stay stable; internal wiring may need adjustment.
- **BREAKING (config)**: Axon 5 may have renamed properties under `axon.*` in `application.yaml`. Specifically the snapshot-trigger threshold (`axon.snapshot.trigger.threshold`) and event-handling processor configuration (`axon.eventhandling.processors.*`) need verification against Axon 5's `application.properties` reference.
- **BREAKING (database)**: If Axon 5 changed the JPA event-store schema (column types, additional tables, removed entities like the dead-letter setup), a *new* Liquibase changeset (`0002-axon-5-schema-migration.yaml`) handles the diff. Per ADR-0009 the existing baseline is never edited; this is exactly the case that flow was designed for.
- **BREAKING (Snapshotter workaround removal)**: `AxonConfig.snapshotTriggerDefinition` currently has no defensive nullability; under Axon 5's restored auto-config it must work straightforwardly.
- **NEW**: `axon-bom` library entry in `gradle/libs.versions.toml` keeps the same Gradle alias (`axon-bom`) but now resolves to the renamed artifact, to avoid touching every `build.gradle.kts` import.
- **REMOVED**: nothing structural — the architecture (hexagonal modules, CQRS, event sourcing) is preserved. Only the framework version moves.
- **DOCS**:
  - **NEW ADR-0010** — Axon 5 baseline; rationale, what moved, link from arc42 §9.
  - arc42 §2 compatibility-matrix row for Axon updated (4.9.3 → 5.1.0; note the new BOM artifact id).
  - arc42 §11 (Risks) — the "Axon-Boot-4 incompatibility" entry is removed, replaced with "Axon 5 messaging-model adoption is partial; sagas, deadlines, and dead-letter queues not yet wired" if applicable.
  - README — no change expected; module-dependency rule still holds.

Non-goals for this change:
- No new functional behaviour. The `/v1/starter/**` API surface is unchanged.
- No move from Axon 5.1.0 to a 5.x milestone or RC. We track GA only.
- No introduction of Axon Server or Axon Cloud. `axon.axonserver.enabled=false` stays.
- No adoption of Axon 5 reactor types in our public ports. `CompletableFuture` remains the boundary type.

## Capabilities

### New Capabilities

(None.)

### Modified Capabilities

- `platform-baseline`: One requirement (`Aggregate framework alignment`) modified to reflect Axon 5.1.0 as the chosen GA, and a second requirement (`Per-environment compatibility matrix`) modified to update the Axon row.

## Impact

- **Code**:
  - `gradle/libs.versions.toml` — `axonBom` value bump; `axon-bom` library `module` coordinate change; `axon-spring-boot-starter` groupId change. Library entries for `axon-modelling`, `axon-eventsourcing`, `axon-test` keep their groupId (`org.axonframework`) per the new BOM.
  - `domain/**` — every Axon import in `SkyAggregate`, command/event/query classes, aggregate identifier annotation. Estimated 15–20 import-line changes.
  - `service/**` — `SkyCommandServicePrimary`, `SkyQueryServicePrimary` — gateway interfaces likely renamed/repackaged; ~5 import lines.
  - `infrastructure/**` — `SkyProjection` (`@ProcessingGroup`, `@EventHandler`, `@QueryHandler`); `AxonConfig` (snapshot trigger API likely changed). ~5–10 lines.
  - `app/src/main/resources/application.yaml` — Axon property keys verified against Axon 5 reference; snapshot trigger and processor segmentation possibly renamed.
  - `infrastructure/src/main/resources/db/changelog/0002-axon-5-schema-migration.yaml` (new, *only if* Axon 5 changed the JPA event-store schema; otherwise this file is not added).
- **Tests**:
  - `SkyAggregateTest` — Axon's `AggregateTestFixture` may have moved; assertions may need adjustment for Axon 5's event-emission semantics.
  - `SkyCommandServicePrimaryTest`, `SkyQueryServicePrimaryTest` — gateway type changes.
  - `SkyEndToEndIT` — should pass without code changes once the wiring is fixed.
- **CI**:
  - `verifyMigrationCoverage` may report changed entity bytecode if Axon 5's JPA entities are repackaged. The override marker `[no-migration]` in the upgrade commit covers that case (we add a 0002 changeset only if the schema actually moved).
- **Runtime**:
  - First start against an existing dev database: Liquibase finds `databasechangelog` already populated, applies the new `0002-...` changeset if present, and Hibernate `validate` confirms.
  - First start against a fresh DB: Liquibase applies both `0001` (Axon 4.9 baseline) and `0002` (Axon 5 deltas) in order. Identical end state.
- **Docs**:
  - ADR-0010 added.
  - arc42 §2 row updated for Axon.
  - arc42 §9 ADR list extended.
- **Downstream changes unblocked**:
  - The Helm / k8s deployment proposal (`helm-k8s-deployment`) is currently blocked because the app does not boot. After this change it does.

Risk: Axon 5's redesigned messaging model could touch APIs we depend on more than expected. Mitigation: this change is scoped to "make the existing app work on Axon 5"; any new Axon 5 features (deadlines, sagas in the new model, dead-letter UI, dynamic command routing) are explicit follow-up work, not this change.
