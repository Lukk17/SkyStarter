## Context

After `upgrade-spring-boot-4` and `replace-ddlauto-with-liquibase` landed, the working tree is on Spring Boot 4.0.6 / Java 25 / Hibernate 7 / Jackson 3 / Liquibase 5.0.2, with Axon Framework still at **4.9.2 (resolved via `axon-bom 4.9.3`)**. The integration test (`SkyEndToEndIT`) confirms the Axon ↔ Boot 4 incompatibility: `JpaEventStoreAutoConfiguration` doesn't fire, no `EventStore` bean is created, the aggregate-repository factory throws *"Default configuration requires the use of event sourcing"*. Unit tests (43) are green; the failure is entirely at the Axon-Boot4 seam.

A naive bump of `axonBom` to `5.1.0` was attempted as a probe and confirmed:
- The BOM artifact id is **renamed**: `org.axonframework:axon-bom` (4.x) → `org.axonframework:axon-framework-bom` (5.x).
- The Spring Boot integration is **moved**: `axon-spring-boot-starter` is now under groupId `org.axonframework.extensions.spring`. Same artifact id, same version (5.1.0), separate Maven coordinate.
- Core framework artifacts (`axon-modelling`, `axon-eventsourcing`, `axon-messaging`, `axon-test`) remain under `org.axonframework` and are managed by the new BOM.
- Compile against Axon 5.1.0 produced **21 errors in `domain/` alone** before any other module was reached. All errors were import-line — Axon 5 reorganised packages substantially.

Constraints:
- The hexagonal module boundary stays as it is (ADR-0001).
- The `/v1/starter/**` API surface MUST not change.
- Liquibase + the existing baseline (`0001-axon-event-store-baseline.{yaml,sql}`) stays. If Axon 5 changes the JPA event-store schema, that's a new `0002-...` changeset, never an edit to `0001`.
- Public domain ports (`SkyCommandService`, `SkyQueryService`) keep their `CompletableFuture` signatures — Axon 5's new reactor types do not leak across the port boundary.

Stakeholders: maintainer, downstream forks, Helm/k8s deployment work that is currently blocked.

## Goals / Non-Goals

**Goals:**
- Make `:app:test` green end-to-end on Axon 5.1.0 + Spring Boot 4.0.6 + Java 25.
- Update the version catalog cleanly: BOM rename, starter groupId move, no version pins on individual Axon artifacts.
- Migrate every Axon import in `domain/`, `service/`, `infrastructure/` to the new package paths.
- If the Axon 5 JPA event-store schema differs from 4.9.x, capture the diff in a new `0002-axon-5-schema-migration.yaml` changeset (per ADR-0009).
- Add ADR-0010 documenting the upgrade and the BOM rename.
- Update arc42 §2 (compatibility matrix) and §9 (ADR list).

**Non-Goals:**
- No new Axon 5 features (sagas in the new style, deadlines, dead-letter UI, dynamic command routing). Plain CRUD-on-aggregate stays.
- No move to Axon 5 reactor types in our public ports. `CompletableFuture` is the boundary.
- No Axon Server adoption.
- No move to Axon's new "DCB" (Dynamic Consistency Boundary) model — out of scope; we keep the aggregate-id consistency model.

## Decisions

### D1. BOM coordinate change is the single source of the rename

The `axon-bom` Gradle alias in `gradle/libs.versions.toml` keeps its name, but its `module` field flips from `org.axonframework:axon-bom` to `org.axonframework:axon-framework-bom`. Every `build.gradle.kts` already imports `platform(libs.findLibrary("axon-bom").get())` — they don't change. The rename is one-line and contained.

**Alternatives considered:** rename the alias too (e.g. `axon-framework-bom`). Rejected — every module's build script would change; not worth the noise.

### D2. Starter groupId change is the second one-liner

`axon-spring-boot-starter` library entry's `module` field changes from `org.axonframework:axon-spring-boot-starter` to `org.axonframework.extensions.spring:axon-spring-boot-starter`. Catalog alias unchanged; everywhere we write `libs.axon.spring.boot.starter` stays the same.

**Alternatives considered:** add a new alias (`axon-extensions-spring-boot-starter`) and migrate references. Rejected — same reason as D1.

### D3. Migrate imports module-by-module, in dependency order

Order: `domain` → `service` → `infrastructure` → `app`. Why this order: the build itself enforces it (each downstream module won't compile until its predecessor does), and it isolates errors. We absorb 21 errors in `domain` first, then look at `service`, etc.

The probe established that all 21 `domain` errors are import-line. The mechanical fix sequence:
1. Run `./gradlew :domain:compileJava`.
2. Read each compiler error; resolve its symbol against Axon 5's published Javadoc (or the JAR's `META-INF/services` manifest if Javadoc is offline).
3. Fix the import; recompile; repeat.

**Alternatives considered:** big-bang `find`/`sed` import rewrites. Rejected — Axon 5 reorganisation is not a clean prefix-rename; some classes split across multiple new packages and need case-by-case judgement.

### D4. Aggregate API: keep our hand-coded `SkyAggregate` rather than adopt Axon 5's new model

Axon 5 ships an alternative aggregate model based on **DCB (Dynamic Consistency Boundary)** that doesn't require an aggregate identifier. Our existing `SkyAggregate` uses the classic `@Aggregate` + `@AggregateIdentifier` model, which Axon 5 still supports for backwards compatibility. We stay on the classic model — adopting DCB is a separate strategic decision (different domain modelling, different testability story).

**Alternatives considered:** rewrite `SkyAggregate` for DCB. Rejected — out of scope for an upgrade. Open follow-up if the team later decides to demonstrate DCB in the template.

### D5. Snapshot trigger API: adapt or drop

Axon 5 may have changed how snapshot triggers attach to aggregates. Three branches:
- (a) `EventCountSnapshotTriggerDefinition` exists, takes `Snapshotter` + threshold → minimal change to `AxonConfig`.
- (b) The class moved/renamed → update import + same constructor.
- (c) The API is fundamentally different (e.g. requires a `SnapshotPolicy` interface) → adapt; the `@Aggregate(snapshotTriggerDefinition = "...")` reference in `SkyAggregate` may need to change.

We commit to whichever branch reality presents and document in ADR-0010. If branch (c) requires significant rework, we drop snapshots in this change and add them back in a separate, scoped follow-up — not gating the upgrade on snapshots is acceptable because (i) the template ships with `axon.snapshot.trigger.threshold: 5` for demo only, and (ii) production tunes that value per-environment anyway.

### D6. JPA event-store schema diff handling

Axon 5 *may* change the schema for `domain_event_entry`, `snapshot_event_entry`, `token_entry`, `saga_entry`, `association_value_entry`, or `dead_letter_entry`. Detection: after the import migration compiles, run `:app:test` and read any Hibernate `Schema validation: ...` errors. Each error becomes a column add / drop / rename / type-change in the new `0002-axon-5-schema-migration.yaml` changeset. If no schema diff is needed, the file is not added — the existing baseline stays the only changeset.

**Alternatives considered:** regenerate the baseline from scratch under Axon 5. Rejected — violates ADR-0009's "baseline is a frozen historical artefact" principle.

### D7. Configuration property migration

Axon 5 may rename properties under `axon.*`:
- `axon.axonserver.enabled` → likely stable.
- `axon.serializer.{events,messages,general}` → likely stable.
- `axon.eventhandling.processors.<name>.{mode,initial-segment-count}` → check Axon 5 reference; possibly renamed.
- `axon.snapshot.trigger.threshold` → custom (defined by us in `application.yaml` for our `@Bean`); stable as long as we keep the bean.

Process: after import migration compiles and runtime starts, watch for Spring Boot's "Configuration property … did not match" warnings; address each individually. The Spring Boot **properties-migrator** dependency (already on the Boot 4 BOM) helps here — we add it as `developmentOnly` for one apply cycle, then remove.

### D8. Test fixture API

`AggregateTestFixture<SkyAggregate>` is from `axon-test`. Axon 5's `axon-test` may have a different fixture API or move the class. The unit test `SkyAggregateTest` is the canary — if it compiles, the fixture API is stable; if not, we adapt.

The expected change is that fixture assertions like `expectEvents(...)`, `expectMarkedDeleted()`, and `expectException(...)` keep their names but their parameter types / matcher integration may shift (Axon 5 may have replaced Hamcrest with AssertJ-style matchers). The test stays expressive either way.

### D9. ADR-0010 + arc42 update + property migration cleanup

- `docs/architecture/decisions/0010-upgrade-to-axon-5.md` — new ADR. Headline points: BOM rename, starter groupId move, why we stay on classic-aggregate (D4), snapshot-trigger branch chosen (D5), schema-diff outcome (D6), property migration outcome (D7).
- arc42 §2 compatibility-matrix Axon row updated.
- arc42 §9 ADR list extended.
- arc42 §11 Risks: any remaining Axon-Boot4 risk row removed.
- README — no change expected.

## Risks / Trade-offs

- **Axon 5 messaging model is async-first.** If `CommandGateway.send` returns something incompatible with our `CompletableFuture<UUID>` shape, we adapt at the service layer. Mitigation: the `service` module is small (two classes), the rewrite is bounded.
- **Aggregate-test fixture incompatibility.** If `AggregateTestFixture` is gone or radically different, the seven `SkyAggregateTest` cases need rewrite. Acceptable — they're small and the assertions are simple. Worst case we lose one round of test-greenness while we redo them.
- **Hidden Axon 5 schema changes the JPA event-store autoconfig assumes.** Hibernate `validate` failures are the detection mechanism; per-failure mitigation is a column add/drop in `0002-...yaml`.
- **Axon 5 dropped legacy `legacyjpa` packages.** Not relevant to us — we never used them.
- **Property rename surfaces only at runtime.** Mitigation: add `spring-boot-properties-migrator` for one apply cycle (D7); remove afterwards.
- **DCB tempts scope creep.** It's tempting to "modernise" the aggregate while we're in the file. Resist. ADR-0010 explicitly notes DCB is out of scope.
- **Test profile's `OAuth2ResourceServerAutoConfiguration` exclusion.** If Axon 5 introduces a new conditional dependency on Spring Security beans, the existing exclusion in `application-test.yaml` may need adjustment. Detect by re-running `:app:test`.

## Migration Plan

1. **Catalog changes** — `axonBom` value → `5.1.0`; `axon-bom` `module` → `org.axonframework:axon-framework-bom`; `axon-spring-boot-starter` `module` → `org.axonframework.extensions.spring:axon-spring-boot-starter`. Run `./gradlew :infrastructure:dependencies` and confirm Axon 5.1.0 resolves; build expected to fail at `:domain:compileJava`.
2. **`domain` import migration** — fix all 21 errors. Recompile until green.
3. **`service` import migration** — fewer errors expected (~5).
4. **`infrastructure` import migration** — `SkyProjection`, `AxonConfig` adjusted.
5. **`app` smoke** — `./gradlew clean compileJava compileTestJava` green.
6. **Unit tests** — `./gradlew :domain:test :service:test :infrastructure:test`. Adapt `AggregateTestFixture` calls if needed.
7. **Integration test** — `./gradlew :app:test` (Docker required). Iterate:
   - Hibernate validate errors → add `0002-axon-5-schema-migration.yaml` columns.
   - Spring property warnings → update `application.yaml` keys; once stable, remove `properties-migrator`.
   - Bean wiring errors → adjust `AxonConfig` per D5 / D6.
8. **Snapshot decision** — confirm whether D5 branch (a), (b), or (c) applies; document in ADR-0010.
9. **Verification gate** — `./gradlew clean build` green; `:app:test` green; `dependencyCheckAnalyze` no new high-severity findings vs. pre-Axon-5 baseline.
10. **ADR + arc42** — write 0010, update §2 and §9.

Rollback: revert the upgrade commit. The `axon-bom` 4.9.3 reverts atomically because the alias change is one line; no data loss.

## Open Questions

- **Is `AggregateTestFixture` API stable or did it move under `axon-test` 5.x?** Decides scope of test rewrites in step 6.
- **Does Axon 5 add/remove JPA entities that would affect Liquibase?** Decides whether `0002-...yaml` exists.
- **Does Axon 5 still ship a `Snapshotter` interface or did it become a `SnapshotPolicy`?** Decides D5 branch.
- **Spring Modulith + Axon 5 interaction.** We have the Spring Modulith BOM imported but no Modulith libs in use. If Axon 5 plays better with Modulith's event-publication infra, that's a follow-up exploration — not this change.
- **Does anything in `axon.eventhandling.processors.*` config get renamed?** Verifiable in ~5 minutes by running the app and watching property-binder warnings.
