## 1. Catalog: BOM rename + starter groupId

- [ ] 1.1 In `gradle/libs.versions.toml`, bump `axonBom` from `4.9.3` to `5.1.0`. Update the inline comment URL to point at `mvnrepository.com/artifact/org.axonframework/axon-framework-bom`.
- [ ] 1.2 Change the `axon-bom` library entry's `module` from `org.axonframework:axon-bom` to `org.axonframework:axon-framework-bom`. Keep the alias name (`axon-bom`) so no `build.gradle.kts` import line needs to change.
- [ ] 1.3 Change the `axon-spring-boot-starter` library entry's `module` from `org.axonframework:axon-spring-boot-starter` to `org.axonframework.extensions.spring:axon-spring-boot-starter`. Keep the alias name.
- [ ] 1.4 Run `./gradlew :infrastructure:dependencies --configuration runtimeClasspath | grep axon` and confirm: the BOM resolves to `org.axonframework:axon-framework-bom:5.1.0`; the starter resolves to `org.axonframework.extensions.spring:axon-spring-boot-starter:5.1.0`; core artifacts (`axon-modelling`, `axon-eventsourcing`, `axon-test`) resolve to `org.axonframework:*:5.1.0`.

## 2. `domain` module import migration

- [ ] 2.1 Run `./gradlew :domain:compileJava`. Capture the full list of compile errors.
- [ ] 2.2 Fix imports in `SkyAggregate.java`:
  - `org.axonframework.commandhandling.CommandHandler` → resolve to its Axon 5 location (likely `org.axonframework.messaging.annotations.CommandHandler` or similar — verify against the JAR).
  - `org.axonframework.eventsourcing.EventSourcingHandler` → Axon 5 location.
  - `org.axonframework.modelling.command.AggregateLifecycle` → Axon 5 location.
  - `org.axonframework.modelling.command.AggregateIdentifier` → Axon 5 location.
  - `org.axonframework.spring.stereotype.Aggregate` → Axon 5 location.
- [ ] 2.3 Fix imports in `CreateSkyCommand.java`, `UpdateSkyCommand.java`, `DeleteSkyCommand.java`:
  - `org.axonframework.modelling.command.TargetAggregateIdentifier` → Axon 5 location.
- [ ] 2.4 Recompile `:domain:compileJava` until green.
- [ ] 2.5 Run `./gradlew :domain:test`. Adapt `AggregateTestFixture` import (likely still `org.axonframework.test.aggregate.*` but verify) and any matcher API changes.

## 3. `service` module import migration

- [ ] 3.1 Run `./gradlew :service:compileJava`. Capture errors.
- [ ] 3.2 Fix imports in `SkyCommandServicePrimary.java`:
  - `org.axonframework.commandhandling.gateway.CommandGateway` → Axon 5 location.
- [ ] 3.3 Fix imports in `SkyQueryServicePrimary.java`:
  - `org.axonframework.queryhandling.QueryGateway` → Axon 5 location.
- [ ] 3.4 If `CommandGateway.send` / `QueryGateway.query` signatures changed (e.g. now return Axon 5's `MessageStream` or a `Mono`), adapt the service methods to keep the public ports' `CompletableFuture` shape stable. Add an adapter conversion if needed; do **not** propagate Axon 5's reactive types across the port boundary.
- [ ] 3.5 Recompile `:service:compileJava` and `:service:test` until green.

## 4. `infrastructure` module import migration

- [ ] 4.1 Run `./gradlew :infrastructure:compileJava`. Capture errors.
- [ ] 4.2 Fix imports in `SkyProjection.java`:
  - `org.axonframework.config.ProcessingGroup` → Axon 5 location.
  - `org.axonframework.eventhandling.EventHandler` → Axon 5 location.
  - `org.axonframework.queryhandling.QueryHandler` → Axon 5 location.
- [ ] 4.3 Fix imports in `AxonConfig.java`:
  - `org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition` → Axon 5 location, if it still exists. If the snapshot-trigger API changed, follow design D5: branch (a) trivial → branch (b) renamed → branch (c) **drop snapshots in this change** and add a TODO + ADR-0010 footnote.
  - `org.axonframework.eventsourcing.Snapshotter` → Axon 5 location.
- [ ] 4.4 Recompile `:infrastructure:compileJava` and `:infrastructure:test` until green.

## 5. Full clean compile + unit test pass

- [ ] 5.1 `./gradlew clean compileJava compileTestJava` green across all four modules.
- [ ] 5.2 `./gradlew :domain:test :service:test :infrastructure:test` green. **All 43 unit tests must pass.** If any aggregate-fixture test has been adapted to a new API, the count is still 43 (no test deleted or skipped without explicit ADR justification).

## 6. Integration test — first run

- [ ] 6.1 Run `./gradlew :app:test` with Docker available. Capture failures.
- [ ] 6.2 If Hibernate `validate` reports missing/extra/wrong-type columns: read each carefully, decide whether it's an Axon 5 schema change or a pre-existing baseline drift. For Axon 5 schema changes, create `infrastructure/src/main/resources/db/changelog/0002-axon-5-schema-migration.yaml` with explicit `addColumn`, `dropColumn`, `modifyDataType`, etc. Add the include line to `db.changelog-master.yaml`. **Never edit `0001-*.{yaml,sql}`** — per ADR-0009, baseline is a frozen artefact.
- [ ] 6.3 If Spring Boot reports "Configuration property 'axon.…' did not match any known property": (a) add `developmentOnly("org.springframework.boot:spring-boot-properties-migrator")` to `app/build.gradle.kts` for the duration of this task, (b) re-run, (c) follow the migrator's suggestions, (d) update `app/src/main/resources/application.yaml` and `app/src/test/resources/application-test.yaml`, (e) **remove the migrator dependency** before final verification.
- [ ] 6.4 If bean-wiring errors persist (`No qualifying bean of type ...`): consult Axon 5's auto-configuration source for the new bean names / conditions. Document any required `@Bean` overrides in `AxonConfig`.

## 7. Integration test — green

- [ ] 7.1 Re-run `./gradlew :app:test` after each fix in §6 until all 4 IT cases pass: `fullCqrsLifecycle_createUpdateGetDelete`, `createWithBlankName_returns400`, `unauthenticated_isRejected`, plus `AppApplicationTests.contextLoads`.
- [ ] 7.2 Capture the final Axon 5 startup log lines (Liquibase application, Axon component initialisation, processor segment claims) into `openspec/changes/upgrade-axon-5/runtime-evidence.md` for archival.

## 8. ADR + arc42

- [ ] 8.1 Add `docs/architecture/decisions/0010-upgrade-to-axon-5.md`. Sections: status (Accepted), date, deciders, context (Axon-Boot4 incompatibility found in `:app:test`; Axon 5.1.0 GA available), decision drivers, options considered (stay on 4.9.x with workarounds vs. upgrade to 5.1.0 vs. wait for 4.10), decision (5.1.0), consequences (positive: Boot 4 compat, async-first messaging future-ready; negative: API moves, `axon-spring-boot-starter` groupId change), links to design D4 (classic aggregate retained), D5 (snapshot-trigger branch chosen), D6 (schema diff outcome).
- [ ] 8.2 Update `docs/architecture/arc42/arc42.md` §2 (Architecture constraints) — Axon row: version → 5.1.0; BOM artifact id → `axon-framework-bom`; starter groupId → `org.axonframework.extensions.spring`.
- [ ] 8.3 Update arc42 §9 (Architectural decisions list) — add ADR-0010.
- [ ] 8.4 Update arc42 §11 (Risks) — remove any "Axon-Boot4 incompatibility" item; if Axon 5's DCB or async-messaging adoption is partial, add a brief item there.

## 9. Final verification

- [ ] 9.1 `./gradlew clean build` is green on JDK 25 / Boot 4 / Axon 5.
- [ ] 9.2 `./gradlew :app:test` is green.
- [ ] 9.3 `./gradlew dependencyCheckAnalyze` shows no NEW high-severity findings (CVSS ≥ 7) vs. pre-Axon-5 baseline. Update suppression file only for genuine false positives.
- [ ] 9.4 `verifyMigrationCoverage` task passes (the `[no-migration]` override marker may be needed in the upgrade commit if no `0002-...yaml` was added but Axon's repackaged `@Entity` classes' bytecode hash differs).
- [ ] 9.5 `./gradlew :infrastructure:liquibaseValidate` against a local Postgres still passes.
- [ ] 9.6 ADR-0010 exists and is reachable from arc42 §9. ADR-0007's `Superseded by 0009` reference still valid (this change does not affect ADR-0007).
- [ ] 9.7 `gradle/libs.versions.toml` audit: every `[versions]` key still falls into one of the three categories from the `platform-baseline` spec (BOM, plugin, no-BOM-coverage).
- [ ] 9.8 Spring Boot's properties-migrator dependency, if added during §6.3, has been removed.

## 10. Cleanup and archive

- [ ] 10.1 Remove any `// TODO upgrade-axon-5` markers introduced during the work.
- [ ] 10.2 Squash WIP commits into a single upgrade commit (the per-module migrations are too entangled to split usefully).
- [ ] 10.3 Run `/opsx:archive upgrade-axon-5` to merge the modified `platform-baseline` deltas into `openspec/specs/`, and move the change folder to `openspec/changes/archive/`.
