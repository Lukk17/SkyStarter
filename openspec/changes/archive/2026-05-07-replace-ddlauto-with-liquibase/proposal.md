## Why

The template currently runs `spring.jpa.hibernate.ddl-auto: update` against PostgreSQL. ADR-0007 explicitly accepted this as a *template-only* shortcut and named its supersede trigger: *"this code is being promoted past template status"*. That promotion is now imminent — the next change in the queue is the Helm / Kubernetes deployment that will run this service in real environments.

`ddl-auto=update` in production is operationally dangerous: it silently mutates schema on every startup, has no rollback story, can't be code-reviewed, and works in exactly one direction. Before any production deployment exists, we replace it with deterministic, version-controlled migrations. **Liquibase** is the chosen tool (mature, multi-DB, declarative changelog format, strong rollback semantics, Spring Boot auto-config out of the box).

This change also **baselines the current Axon Framework event-store schema** — the tables Axon's JPA event store creates (`domainevententry`, `snapshotevententry`, `tokenentry`, `saga_entry`, `association_value_entry`). Without a baseline, the first migration would either re-create those tables (and conflict with Axon) or skip them silently (and leave dev/prod in inconsistent states). The baseline captures what `ddl-auto=update` produced once, and freezes it.

## What Changes

- **BREAKING (operational)**: `spring.jpa.hibernate.ddl-auto` set to `validate` in `application.yaml`. Schema mutations no longer happen at startup — Liquibase is the only path.
- **NEW**: Liquibase as a runtime dependency (`spring-boot-starter`-style auto-config; managed by the Spring Boot BOM).
- **NEW**: `infrastructure/src/main/resources/db/changelog/db.changelog-master.yaml` as the changelog entry point.
- **NEW**: a baseline changeset that captures Axon's event-store DDL as it currently exists on a fresh `ddl-auto=create` run. Marked `runOnChange: false` and gated by an empty-DB precondition so it does not re-execute on existing environments.
- **NEW**: `liquibase` Gradle plugin (optional convenience for `./gradlew liquibaseGenerateChangelog`, `./gradlew liquibaseDiff`) — wired only on the `infrastructure` module since that's where the JPA datasource lives.
- **NEW**: a CI verification step that asserts every push includes a corresponding Liquibase changeset *or* declares "no schema change". Mechanism: a Gradle task that fails if the JPA entity classpath has changed without a new changelog file.
- **NEW**: integration-test wiring — Liquibase runs against the Postgres Testcontainer in `SkyEndToEndIT`; if a migration is broken, the test suite fails before deployment.
- **REMOVED**: nothing yet. The dialect class (`ByteaEnforcedPostgresSQLDialect`) stays — it controls Hibernate's column type generation, which is orthogonal to migrations.
- **DOCS**:
  - **Supersede ADR-0007**: status changed to `Superseded by 0009`. Body kept for history.
  - **NEW ADR-0009** — Liquibase + Axon-baseline strategy.
  - arc42 §11 (Risks & technical debt) — `ddl-auto=update` row removed; replaced with a "migrations live in `db/changelog/`" entry.
  - README — short note on how to add a new migration.

## Capabilities

### New Capabilities

- `database-migrations`: Defines how schema changes are authored, versioned, applied, and verified for the JPA datasource. Lives at `openspec/specs/database-migrations/spec.md`. Future changes (e.g. adding a new aggregate that needs its own table, or swapping the event store) update this spec via deltas.

### Modified Capabilities

- `platform-baseline`: One requirement modified to reflect that Liquibase is now part of the supported runtime version contract (it appears in the per-environment compatibility matrix and gets BOM-managed via `spring-boot-dependencies`).

## Impact

- **Code**:
  - `app/src/main/resources/application.yaml` — `ddl-auto: update` → `validate`; new `spring.liquibase` block.
  - `app/src/main/resources/application-local.yaml` — possibly `spring.liquibase.contexts: local` to allow seed data; otherwise unchanged.
  - `app/src/test/resources/application-test.yaml` — `ddl-auto: validate` (was `create-drop`); Liquibase becomes the schema source.
  - `infrastructure/build.gradle.kts` — add Liquibase runtime dep + plugin.
  - `infrastructure/src/main/resources/db/changelog/db.changelog-master.yaml` (new) and the baseline `0001-axon-event-store-baseline.yaml` (new).
  - `gradle/libs.versions.toml` — `liquibase-core` library entry (Boot-BOM-managed, no version pin) and the Liquibase Gradle plugin entry.
- **CI**:
  - GHA workflow gains a `liquibase update --dry-run` step against a fresh Postgres container as a sanity check.
  - The "changelog or no-schema-change" guard runs as a Gradle task in the same workflow.
- **Runtime**:
  - First run on an existing dev DB: Liquibase initialises its tracking tables (`databasechangelog`, `databasechangeloglock`) and **marks the baseline changeset as already-applied** (precondition: tables exist). No data movement.
  - First run on an empty DB: Liquibase applies the baseline, creating Axon tables. Identical end state to the previous `ddl-auto=update`.
- **Tests**:
  - `SkyEndToEndIT` continues to pass — the Postgres Testcontainer starts empty, Liquibase runs the baseline, then Axon and the projection processor proceed normally.
  - All 43 existing unit tests are unaffected (none of them touch the JPA layer directly).
- **Docs**:
  - ADR-0007 superseded (status update, no content change to the body).
  - ADR-0009 added.
  - arc42 §11 risks table updated.
  - `docs/architecture/diagrams/` — no new diagrams required; the deployment diagram in the next change folder will reference migrations.
- **Downstream changes unblocked**:
  - The Helm / k8s deployment (`helm-k8s-deployment`, the next OpenSpec change) can now ship a `pre-install/pre-upgrade` Job that runs `liquibase update`. Without this change, that Job has nothing to invoke.
