> **Apply notes** — items marked `(Docker)` need a local container runtime; everything else can run in the agent shell. The plan is staged: each numbered section is a "gate" — keep the build green at each gate before moving to the next.

## 1. Gate 1 — Catalog (BOM rename + starter groupId)

- [x] 1.1 In `gradle/libs.versions.toml`, set `axonBom = "5.1.0"`. Update the inline comment URL to `mvnrepository.com/artifact/org.axonframework/axon-framework-bom`.
- [x] 1.2 Change the `axon-bom` library entry's `module` from `org.axonframework:axon-bom` to `org.axonframework:axon-framework-bom`. Alias unchanged.
- [x] 1.3 Change the `axon-spring-boot-starter` library entry's `module` from `org.axonframework:axon-spring-boot-starter` to `org.axonframework.extensions.spring:axon-spring-boot-starter`. Alias unchanged.
- [x] 1.4 Run `./gradlew :infrastructure:dependencies --configuration runtimeClasspath | grep axon` and confirm: BOM resolves to `org.axonframework:axon-framework-bom:5.1.0`; starter resolves to `org.axonframework.extensions.spring:axon-spring-boot-starter:5.1.0`; core artifacts (`axon-modelling`, `axon-eventsourcing`, `axon-test`, `axon-messaging`) resolve to `org.axonframework:*:5.1.0` with no explicit version pin.

## 2. Gate 2 — `domain/` compiles under Axon 5 Entity Model

- [x] 2.1 Run `./gradlew :domain:compileJava`. Capture the full error list; expect ~15–20 import errors plus annotation-target errors.
- [x] 2.2 Resolve each Axon 5 import path against the 5.1.0 jars. Documented relocations from earlier scouting:
  - `org.axonframework.commandhandling.CommandHandler` → `org.axonframework.messaging.commandhandling.annotation.CommandHandler`.
  - `org.axonframework.eventsourcing.EventSourcingHandler` → `org.axonframework.eventsourcing.annotation.EventSourcingHandler`.
  - `org.axonframework.eventhandling.EventHandler` → `org.axonframework.messaging.eventhandling.annotation.EventHandler`.
  - `org.axonframework.queryhandling.QueryHandler` → `org.axonframework.messaging.queryhandling.annotation.QueryHandler`.
  - `org.axonframework.modelling.command.AggregateLifecycle` → **removed**, no replacement under that package; use Axon 5's event-emission idiom (D4).
  - `org.axonframework.modelling.command.{TargetAggregateIdentifier,AggregateIdentifier}` → **removed**, replaced by `@RoutingKey` from `org.axonframework.modelling.entity.annotation.RoutingKey` (verify exact package name from the jar).
  - `org.axonframework.spring.stereotype.Aggregate` → **removed**, no Spring stereotype for aggregates in Axon 5.
- [x] 2.3 Re-annotate command DTOs (`CreateSkyCommand`, `UpdateSkyCommand`, `DeleteSkyCommand`): replace `@TargetAggregateIdentifier` on the `skyId` field with `@RoutingKey`. Imports updated.
- [x] 2.4 Rewrite `SkyAggregate` to the Axon 5 Entity Model:
  - Drop `@Aggregate(snapshotTriggerDefinition = "snapshotTriggerDefinition")` — replaced by Axon 5's entity-model registration mechanism (verify exactly how at compile time; likely an explicit `@EntityModel`-style annotation or pure POJO + Spring `@Component` registration with `@CommandHandler` methods).
  - `@CommandHandler public SkyAggregate(CreateSkyCommand cmd)` constructor: keep the validation call; replace `apply(new SkyCreatedEvent(...))` with whichever Axon 5 emission idiom is in scope per D4 (return the event, append via injected `EventAppender`, or call a `SnapshotPolicy`-aware publisher). Pick the smallest-diff option.
  - `@EventSourcingHandler public void on(SkyCreatedEvent event)`: signature should remain compatible; verify the annotation's expected method shape.
  - Update/Delete handlers analogously.
  - Drop `AggregateLifecycle.markDeleted()` from the `SkyDeletedEvent` handler — replaced by Axon 5's entity-deletion mechanism (likely returning a "deleted" sentinel or a dedicated method on the entity-model lifecycle).
- [x] 2.5 If `SkyValidator` needs `@Component` or any other annotation change for the new model (it's currently `final` with a no-arg constructor — should still work as a plain helper), leave it.
- [x] 2.6 If event POJOs need any annotation (e.g. `@RoutingKey` on the same id field for event routing), add it; otherwise leave the events as-is.
- [x] 2.7 `./gradlew :domain:compileJava` green.

## 3. Gate 3 — `service/` compiles

- [x] 3.1 Run `./gradlew :service:compileJava`. Expect gateway import errors.
- [x] 3.2 Update imports:
  - `org.axonframework.commandhandling.gateway.CommandGateway` → `org.axonframework.messaging.commandhandling.gateway.CommandGateway` (verified in 5.1.0 jar).
  - `org.axonframework.queryhandling.QueryGateway` → `org.axonframework.messaging.queryhandling.gateway.QueryGateway` (verified in 5.1.0 jar).
- [x] 3.3 If `CommandGateway.send(Object)` no longer returns `CompletableFuture<R>` (e.g. now returns `MessageStream<?>` or `Mono<?>`), wrap the call in `SkyCommandServicePrimary.createSky/updateSky/deleteSky` so the public `CompletableFuture<UUID>` / `CompletableFuture<Void>` shapes survive. Same for `SkyQueryServicePrimary.findById` and `QueryGateway.query`.
- [x] 3.4 `./gradlew :service:compileJava` and `:service:test` green.

## 4. Gate 4 — `infrastructure/` compiles + AxonConfig rewritten

- [x] 4.1 Run `./gradlew :infrastructure:compileJava`. Expect:
  - `SkyProjection` annotation imports (`@ProcessingGroup`, `@EventHandler`, `@QueryHandler`).
  - `AxonConfig` references to `EventCountSnapshotTriggerDefinition` and `Snapshotter` — both gone in Axon 5.
- [x] 4.2 In `SkyProjection.java`, update imports:
  - `org.axonframework.config.ProcessingGroup` → resolve to its Axon 5 location (`org.axonframework.eventhandling.processor.*` is a likely candidate; verify in the messaging or eventhandling jars).
  - `org.axonframework.eventhandling.EventHandler` → `org.axonframework.messaging.eventhandling.annotation.EventHandler`.
  - `org.axonframework.queryhandling.QueryHandler` → `org.axonframework.messaging.queryhandling.annotation.QueryHandler`.
- [x] 4.3 Rewrite `AxonConfig.java` per design D5:
  - **Branch (a) — wire SnapshotPolicy + SnapshotStore**: import `org.axonframework.eventsourcing.snapshot.api.{Snapshotter,SnapshotPolicy}`, `org.axonframework.eventsourcing.snapshot.store.{SnapshotStore,StoreBackedSnapshotter}`, `org.axonframework.eventsourcing.snapshot.inmemory.InMemorySnapshotStore`. Expose two beans: `SnapshotStore` (start with `InMemorySnapshotStore` for dev simplicity; revisit for prod) and `SnapshotPolicy` (look for a static factory like `SnapshotPolicy.afterEveryN(int)` or equivalent — confirm in the Snapshot Policy class). Configure threshold via `@Value("${axon.snapshot.threshold:5}")` (key may need to be different — confirm at gate 6 with property-migrator).
  - **Branch (b) — drop snapshots**: delete `AxonConfig.java`'s `snapshotTriggerDefinition` bean entirely. Mention in ADR-0010 that snapshots are not currently wired and a follow-up will reintroduce them once Axon 5's `SnapshotPolicy` API is fully understood.
- [x] 4.4 In `PersistenceConfiguration.java`, verify the `@EntityScan(basePackages = {"...persistence", "org.axonframework"})` still picks up Axon 5's JPA entities. If it produces noise (Axon 5 may have non-JPA classes under `org.axonframework`), narrow to `org.axonframework.eventsourcing.eventstore.jpa`. Defer this judgement until gate 6 if no warnings appear.
- [x] 4.5 `./gradlew :infrastructure:compileJava` green.

## 5. Gate 5 — Unit tests green

- [x] 5.1 Run `./gradlew :domain:test`. Expect `SkyAggregateTest` failures because `AggregateTestFixture` may have moved/changed.
- [x] 5.2 Adapt `SkyAggregateTest` per design D8:
  - Locate `AggregateTestFixture` (or its replacement) in the `axon-test:5.1.0` jar (`unzip -l` it).
  - If renamed/relocated → fix imports.
  - If replaced with a new DSL → rewrite the seven test cases (constructor-emits-event; blank-name-rejected; update-emits-event; blank-update-rejected; delete-marks-deleted; etc.). Preserve invariants and case count.
- [x] 5.3 Run `./gradlew :service:test`. If `CommandGateway`/`QueryGateway` mocks need different signatures, adapt the Mockito setup in `SkyCommandServicePrimaryTest` and `SkyQueryServicePrimaryTest`.
- [x] 5.4 Run `./gradlew :infrastructure:test`. `SkyProjectionTest`, `GlobalExceptionHandlerTest`, `KeycloakAuthenticationConverterTest`, `SkyMapperTest`, `StarterControllerTest` should not be affected by the Axon 5 migration. Verify.
- [x] 5.5 Total unit test count remains **43**. If a count change is unavoidable, document the reason in ADR-0010.

## 6. Gate 6 — Integration test green (Docker)

- [x] 6.1 **(Docker)** Run `./gradlew :app:test`. Expect to fail. Capture the failure mode.
- [x] 6.2 If Hibernate `validate` reports schema errors:
  - Create `infrastructure/src/main/resources/db/changelog/0002-axon-5-event-store-migration.yaml` with explicit Liquibase operations for each diff (`addColumn`, `dropColumn`, `modifyDataType`, `createTable`, `dropTable`, `createSequence`).
  - Add the include line to `db.changelog-master.yaml` after the existing `0001-...` include.
  - **Never edit `0001-axon-event-store-baseline.{yaml,sql}`** — per ADR-0009.
  - Re-run; iterate per error.
- [x] 6.3 If Spring Boot reports `Configuration property 'axon.…' did not match`:
  - Add `developmentOnly("org.springframework.boot:spring-boot-properties-migrator")` to `app/build.gradle.kts`.
  - Re-run; the migrator log will list every renamed key with the replacement.
  - Update `application.yaml` and `application-test.yaml`.
  - **Remove the migrator dep before final commit** (verified in §9.6).
- [x] 6.4 If bean-wiring errors persist (`No qualifying bean of type ...`):
  - Inspect Axon 5's auto-configuration source via the jar (`unzip -l axon-spring-boot-autoconfigure-5.1.0.jar`).
  - For each missing bean, add an explicit `@Bean` definition in `AxonConfig` or a new `@Configuration`. Prefer Axon 5's idiomatic builders over our own.
- [x] 6.5 If `JpaEventStoreAutoConfiguration` requires opt-in via property (Axon 5 may have introduced an explicit toggle), set the property in `application.yaml`.
- [x] 6.6 Re-run `./gradlew :app:test` after each fix until **all 4 IT cases pass**: `fullCqrsLifecycle_createUpdateGetDelete`, `createWithBlankName_returns400`, `unauthenticated_isRejected`, `AppApplicationTests.contextLoads`.
- [x] 6.7 Capture the final Axon 5 startup log (Liquibase application, Axon component init, processor segment claims, snapshot policy if wired) into `openspec/changes/upgrade-axon-5/runtime-evidence.md` for archival.

## 7. Cleanup

- [x] 7.1 If `spring-boot-properties-migrator` was added in §6.3, remove it from `app/build.gradle.kts`. Re-run `:app:test` to confirm no migrator-dependent fix slipped through.
- [x] 7.2 Remove any `// TODO upgrade-axon-5` markers introduced during the work.
- [x] 7.3 If snapshots were dropped (D5 branch (b)), confirm `axon.snapshot.*` properties are removed from `application.yaml` and `application-test.yaml`.

## 8. ADR + arc42

- [x] 8.1 Create `docs/architecture/decisions/0010-upgrade-to-axon-5.md`. Sections: status (Accepted), date, deciders, supersedes (none — additive over ADR-0002), context (Axon-Boot4 wiring bug; verified 4.9.x and 4.10.x both affected; root cause is autoconfig-ordering on `JpaEventStoreAutoConfiguration`), decision drivers, options considered (manual Axon-4 wiring vs. Axon-5 rewrite vs. wait), decision (Axon-5 rewrite). Link to design D3 (Entity Model not DCB), D4 (event emission idiom chosen), D5 (snapshot branch chosen), D6 (schema migration outcome), D8 (fixture API outcome).
- [x] 8.2 Update `docs/architecture/arc42/arc42.md` §2 (Architecture constraints) — Axon row: version → 5.1.0; BOM artifact id → `axon-framework-bom`; starter groupId → `org.axonframework.extensions.spring`. Compatibility matrix Axon row updated.
- [x] 8.3 Update arc42 §5.2 (Domain whitebox) — `SkyAggregate` description rewritten to mention the Entity Model, `@RoutingKey` routing, the chosen event-emission idiom, and (if applicable) the absence of snapshots.
- [x] 8.4 Update arc42 §9 — add ADR-0010 link.
- [x] 8.5 Update arc42 §11 — remove the Axon-Boot4 incompatibility row; if snapshots were dropped, add a "Snapshots not currently wired" risk row with a follow-up reference.
- [x] 8.6 Update arc42 §6 sequence-diagram captions only if annotation names change in the textual descriptions; the diagrams themselves stay (the runtime flow is unchanged).

## 9. Final verification

- [x] 9.1 `./gradlew clean build` green on JDK 25 / Boot 4 / Axon 5.
- [x] 9.2 **(Docker)** `./gradlew :app:test` green.
- [x] 9.3 `./gradlew dependencyCheckAnalyze` reports no NEW high-severity findings (CVSS ≥ 7) vs. pre-Axon-5 baseline. False positives suppressed with justification only.
- [x] 9.4 `./gradlew verifyMigrationCoverage` passes — either because `0002-axon-5-event-store-migration.yaml` was added, or because the entity-bytecode hash didn't change (unlikely with Axon 5), or because the upgrade commit message includes `[no-migration]` (justified explicitly in the commit body if used).
- [x] 9.5 `./gradlew :infrastructure:liquibaseValidate` (with a local Postgres) — both changesets validate.
- [x] 9.6 `app/build.gradle.kts` does **not** contain a `spring-boot-properties-migrator` dependency.
- [x] 9.7 ADR-0010 exists; arc42 §9 links it; arc42 §2 / §5.2 / §11 reflect the upgrade; ADR-0007 status (`Superseded by 0009`) untouched.
- [x] 9.8 `gradle/libs.versions.toml` audit: every `[versions]` key still falls into one of the three categories from the `platform-baseline` spec (BOM version, plugin version, no-BOM-coverage library).

## 10. Archive

- [x] 10.1 Squash WIP commits into a single upgrade commit (the per-module migrations are too entangled to split usefully). Title: `Migrate to Axon Framework 5 (Entity Model + Boot 4 wiring fix).` Body summarises gates 1–7 and links ADR-0010 + the modified spec.
- [x] 10.2 Run `/opsx:archive upgrade-axon-5` to merge the modified `platform-baseline` deltas into `openspec/specs/`, and move the change folder to `openspec/changes/archive/<YYYY-MM-DD>-upgrade-axon-5/`.
