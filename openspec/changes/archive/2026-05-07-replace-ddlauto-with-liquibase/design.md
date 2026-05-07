## Context

The JPA datasource currently uses `spring.jpa.hibernate.ddl-auto: update`. The schema is whatever Hibernate has deduced from `@Entity` classes plus what Axon's `axon-spring-boot-starter` auto-config has materialised. There are no migrations, no migration tool on the classpath, no version tracking on the schema, and no record of what the "current" schema is supposed to look like.

Two consumers of the schema exist:
1. **Axon Framework** — owns the bulk of the JPA tables (`domainevententry`, `snapshotevententry`, `tokenentry`, `saga_entry`, `association_value_entry`). These are produced by Axon's `axon-eventsourcing` and `axon-spring` JPA entities.
2. **Application code** — currently zero application JPA entities. The application's projections live in MongoDB (`SkyEntity` is a MongoDB document, not a JPA entity).

So the entire JPA surface today is Axon's. That makes the baseline simple but it also means the migration tool has to play nicely with a framework that owns more of the schema than the application does. Constraints:
- `ByteaEnforcedPostgresSQLDialect` forces Hibernate to emit `bytea` for `BLOB` columns (Axon's payload). The baseline must capture that — meaning the baseline DDL must be generated *with* the custom dialect active, not with stock `PostgreSQLDialect`.
- Axon's table layout is stable across Axon 4.x patch versions but has changed historically. The baseline locks us to "Axon 4.9.2 schema". A future Axon major bump (4.x → 5.x) is a separate migration.
- Spring Boot's Liquibase auto-config (when the dependency is present) wires Liquibase to run before JPA initialisation. We rely on that ordering — we do not configure it manually.

Stakeholders: maintainer, anyone forking the template, anyone running it in any environment beyond a local dev DB.

## Goals / Non-Goals

**Goals:**
- Replace `ddl-auto=update` with deterministic, reviewable, version-controlled schema management.
- Capture the current Axon 4.9 schema as a Liquibase **baseline** so existing dev databases keep working.
- Make schema drift between JPA entities and the live DB a **build failure**, not a silent runtime mutation.
- Cover the full path in CI: baseline applies cleanly to a fresh container; migrations work in `SkyEndToEndIT`.
- Supersede ADR-0007 cleanly and add ADR-0009.

**Non-Goals:**
- Not introducing migration tooling for MongoDB. Mongo is schemaless; projections rebuild from events. Out of scope.
- Not switching DBs. PostgreSQL stays.
- Not migrating to Flyway. Liquibase is chosen here; the spec doesn't lock it forever, but a swap is a future change.
- Not adding Liquibase contexts beyond `local` (e.g. environment-specific seed data). Out of scope.
- Not generating the baseline at *runtime*. The baseline is hand-committed once; Liquibase doesn't generate it on the fly.
- Not retro-fitting per-changeset rollback to the baseline (it would be a `dropAll` and we never want to run it; documented).

## Decisions

### D1. Liquibase, not Flyway

We pick Liquibase because:
- **Database abstraction**: Liquibase changesets can express the same DDL against PostgreSQL today and (theoretically) any other supported DB tomorrow. Flyway is SQL-first.
- **Declarative format (YAML/XML/JSON)**: easier to review than raw SQL deltas. Forks adapting to other databases benefit.
- **Better preconditions**: Liquibase's `<preConditions>` lets the baseline detect "tables already exist" without per-environment hand-tweaking. Flyway can do this with `BaselineMigration` but it's clumsier.
- **Spring Boot auto-config parity**: both have first-class auto-config in Spring Boot. Tie.
- **Familiarity**: Liquibase has historically been the choice in this repo (it was present before being removed in commit `4e0575b`); reintroducing it is less context shift.

**Alternatives considered:** Flyway (rejected per above), Liquibase Pro (rejected — paid features we don't need), home-rolled SQL scripts run from a Job (rejected — no tracking, no idempotency).

### D2. YAML changelogs, not XML or SQL

YAML strikes the right balance: human-readable, supports Liquibase's full feature set (preconditions, contexts, labels, rollback blocks), no XML schema imports cluttering files, no per-DB SQL dialect lock-in.

**Alternatives considered:** XML (rejected — verbose, schema imports), pure SQL (rejected — loses preconditions and rollback metadata), JSON (rejected — no comments, awful for review).

### D3. Single master changelog with numbered includes

```
infrastructure/src/main/resources/db/changelog/
├── db.changelog-master.yaml
├── 0001-axon-event-store-baseline.yaml
└── (future) 0002-...yaml
```

The master file contains only `<include>` lines in numerical order. Each individual changeset file owns one logical change. `databaseChangeLog` is the list of `changeSet` blocks.

Numbering is monotonic, never re-used, never re-ordered. We use 4-digit zero-padded numbers (`0001`, `0002`, …) to avoid sort-order surprises and to keep room for ~10 000 changesets before re-numbering becomes a topic.

**Alternatives considered:** date-prefixed (`20260504-...`) — rejected, branch merges produce out-of-order dates that look chronological but aren't; sequential numbers force a deliberate conflict resolution at merge time, which is the correct behaviour.

### D4. Baseline strategy: precondition-gated `ddl-auto=create` snapshot

The baseline changeset's structure:

```yaml
changeSet:
  id: 0001-axon-event-store-baseline
  author: maintainer
  preConditions:
    - onFail: MARK_RAN          # if tables already exist, mark as applied without running
    - not:
      - tableExists: { tableName: domainevententry }
  changes:
    - sqlFile:
        path: 0001-axon-event-store-baseline.sql
  rollback:
    - sql: SELECT 1 -- baseline is intentionally non-rollback-able
```

The companion `.sql` file is the **literal output of `pg_dump --schema-only`** taken from a fresh container after Hibernate created the tables under `ddl-auto=create` with our custom dialect active. We commit it as a binary fact, not as Liquibase abstractions, because:
- Hibernate's DDL is the ground truth for what Axon expects.
- Reverse-engineering it into Liquibase `createTable` blocks risks subtle drift (column collation, default values, index names).
- The dump is reviewable as plain SQL.

The precondition with `onFail: MARK_RAN` covers the legacy case: any existing dev DB where these tables already exist gets the baseline marked as applied (recorded in `databasechangelog`) without re-running it. From that point on, Liquibase tracks all future changes correctly.

**Alternatives considered:**
- `liquibase generateChangeLog` against a live schema — rejected, generates over-decorated XML/YAML that does not round-trip cleanly to PostgreSQL DDL.
- Skipping the baseline and starting changeset numbering from `0001-add-myfeature-table` — rejected, leaves `databasechangelog` claiming to know nothing about Axon's tables, which is a lie that bites the first time someone runs `liquibase status` or attempts a rollback.

### D5. Liquibase Gradle plugin for authoring, not for runtime

The runtime path is unambiguous: Spring Boot auto-config picks up Liquibase from the classpath and runs it before JPA. We do **not** invoke Liquibase from Gradle at deploy time.

The Gradle plugin (`org.liquibase.gradle`) is added only for **author-time convenience**:
- `./gradlew :infrastructure:liquibaseDiff` — diff a developer's local PostgreSQL against the runtime entity model; emit a candidate changeset.
- `./gradlew :infrastructure:liquibaseUpdate` — apply pending changesets to a developer's local DB without booting the application.
- `./gradlew :infrastructure:liquibaseValidate` — sanity-check the changelog syntax.

These tasks are gated behind a `liquibase` configuration block that requires explicit `url`/`username`/`password` properties; they fail noisily if those are missing, so they cannot accidentally run against the wrong database.

**Alternatives considered:** running `liquibase` CLI directly (rejected — extra install step for every developer), invoking via Spring Boot bootRun (rejected — pulls up the whole context just to migrate).

### D6. CI guard: "entity changes require migrations"

A small Gradle task `verifyMigrationCoverage` runs in CI:
1. Computes the SHA of all `@Entity`-annotated classes' bytecode in `infrastructure/build/classes`.
2. Compares against the previous commit's SHA (cached in `build/migration-coverage.sha`).
3. If the SHA changed and no new file has been added under `db/changelog/` since the previous commit, **fail the build** with a message naming the entities that changed and the next available changelog filename.

Override mechanism: a commit message containing `[no-migration]` (typically used when the change is to a `@Transient` field, a method, or a bean that is `@Entity`-annotated for cosmetic reasons but doesn't persist).

This is a heuristic, not a proof. False positives are tolerable (developer adds a `[no-migration]` marker after thinking it through); false negatives are guarded against by the second CI check (the changelog must apply cleanly to a fresh container — if you forgot a migration, integration tests fail).

**Alternatives considered:** parsing JPA metamodel at compile time (rejected — too fragile across Hibernate versions), running `liquibase diff` in CI (rejected — needs a baseline DB and is slow), no guard at all (rejected — that's the whole point of moving off `ddl-auto`).

### D7. `application-test.yaml` switches from `create-drop` to `validate`

Currently the test profile uses `ddl-auto: create-drop` so each test run gets a clean schema. With Liquibase in play:
- Liquibase runs at context startup against the Testcontainer (which is fresh per test class by default).
- Hibernate `validate` confirms the entity model matches.
- No more `create-drop` — the baseline IS the schema.

Side benefit: tests now run against the **same DDL as production**, not Hibernate's "what would I have done?" interpretation. This is the single biggest correctness win of the change.

**Alternatives considered:** keep `create-drop` for tests, run Liquibase only in non-test profiles (rejected — defeats the entire point; tests must exercise the migration path).

### D8. Spring Boot auto-config ordering

Spring Boot's `LiquibaseAutoConfiguration` runs **before** `HibernateJpaAutoConfiguration` because `LiquibaseAutoConfiguration` declares `@AutoConfigureBefore(HibernateJpaAutoConfiguration.class)`. We rely on this. We do **not** add custom `@DependsOn` wiring; the auto-config is sufficient and well-tested.

Edge case: Axon's auto-config also depends on JPA. Liquibase → JPA → Axon is the order; verified by application startup logs in dev.

### D9. ADR lifecycle

- **ADR-0007** (no Liquibase) status changes from `Accepted` → `Superseded by 0009`. Body untouched (history preserved).
- **ADR-0009** (this change) is added. Body covers: why now, why Liquibase over Flyway, baseline strategy, CI guard, supersedes-0007 link.
- arc42 §11 risks table loses the `ddl-auto=update` row.
- arc42 §2 compatibility matrix gains a Liquibase row.

## Risks / Trade-offs

- **Baseline SHA brittleness**: the baseline SQL file is generated once. If we re-generate it under a slightly different Hibernate version, it might differ by a comment or column ordering. Mitigation: the file is committed as-is; future schema bumps are *new* changesets, never edits to the baseline. The baseline is a frozen historical artifact.
- **Existing dev DBs**: any developer or CI environment that already ran `ddl-auto=update` has the Axon tables but no `databasechangelog`. On first run after this change, Liquibase finds the tables, hits the precondition, marks the baseline as `EXECUTED`, and proceeds. **Verified by including a "legacy DB" scenario in the integration test setup.**
- **Axon major upgrade**: if Axon 5 changes the event-store schema, that's a *new* changeset (`0002-axon-5-schema-migration.yaml`) that handles the diff. The baseline stays. The Axon upgrade ADR will document it.
- **CI guard false positives**: a refactor to an `@Entity` class that doesn't change persistence (e.g. adding a constructor) triggers the guard. Override mechanism (`[no-migration]` in commit message) handles it. Acceptable trade-off.
- **Plugin version drift**: the `org.liquibase.gradle` plugin is independent of the Liquibase runtime version. We pin the runtime via the Spring Boot BOM (no version key in `libs.versions.toml`); the plugin gets its own version key. Risk: plugin and runtime go out of sync. Mitigation: plugin is author-time only; runtime is what actually matters.
- **Local dev startup cost**: Liquibase adds ~200-500 ms to startup on a healthy DB (just reads `databasechangelog`). Negligible.

## Migration Plan

1. **Add Liquibase dependency** (`infrastructure/build.gradle.kts`). Build still green.
2. **Generate the baseline SQL** by running a one-shot script: spin up a clean PostgreSQL container, run the app with `ddl-auto=create-drop` against it, `pg_dump --schema-only`, save as `0001-axon-event-store-baseline.sql`. Commit. *This step happens once, by the implementer; it is not a runtime concern.*
3. **Author the baseline changeset** (`0001-axon-event-store-baseline.yaml`) with the precondition gate.
4. **Author the master changelog** (`db.changelog-master.yaml`) with the include line.
5. **Flip `ddl-auto`** to `validate` in `application.yaml` and `application-test.yaml`. Remove `create-drop` from test profile.
6. **Run `SkyEndToEndIT`** — it must pass. Liquibase runs against the fresh Testcontainer, applies the baseline, Hibernate validates, app starts.
7. **Add the Gradle plugin and convenience tasks** to `infrastructure/build.gradle.kts`.
8. **Add the CI guard task** (`verifyMigrationCoverage`) to root build.gradle.kts; wire into `check`.
9. **Update ADR-0007** (status only).
10. **Add ADR-0009**.
11. **Update arc42 §2 and §11**.
12. **Update README**.
13. **Verification gate**: full `./gradlew clean build`, integration test green, no regressions.

Rollback: revert the commit. Liquibase artifacts (`databasechangelog`, `databasechangeloglock`) remain in the DB but are inert; restoring `ddl-auto=update` resumes the previous behaviour. No data loss.

## Open Questions

- **Where does the one-time baseline SQL generation happen?** Option A: a manual step the implementer runs locally (documented in tasks.md). Option B: a CI workflow that produces the file as an artifact and the implementer commits it. **Recommendation: A** for this template-scoped change; B is overkill.
- **Do we want Liquibase contexts (`local`, `prod`)?** Currently no. The first need (e.g. seed data for dev) introduces them — separate change.
- **Should the CI guard be configurable to also require a description of the change?** Out of scope; commit message conventions already cover this.
- **Liquibase plugin Gradle ID**: `org.liquibase.gradle` — confirmed available; pinned via a new `[plugins]` entry in `libs.versions.toml`.
