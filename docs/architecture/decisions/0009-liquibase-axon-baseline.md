# 0009 — Liquibase + Axon event-store baseline

- **Status:** Accepted
- **Date:** 2026-05-04
- **Deciders:** Project maintainer
- **Supersedes:** [ADR-0007](0007-no-liquibase.md)
- **Tags:** persistence, migrations, schema, supersede

## Context and problem statement

ADR-0007 explicitly accepted `spring.jpa.hibernate.ddl-auto: update` as a *template-only* shortcut and named the supersede trigger: *"this code is being promoted past template status"*. The next change in the queue is the Helm / Kubernetes deployment (`helm-k8s-deployment`), which will run this service in real environments. That trigger has fired.

`ddl-auto=update` in production is operationally dangerous:
- silently mutates schema on every startup,
- cannot roll back,
- cannot be code-reviewed (the diff lives in Hibernate's head, not in Git),
- works only in one direction (additive — no drops, no renames),
- diverges between environments because it depends on JPA entity classpath at startup,
- has no idempotency contract on retry.

We replace it with **Liquibase** before any production deployment exists, and we **baseline** the current Axon JPA event-store schema as the first changeset so existing dev databases keep working without data loss.

## Decision drivers

- Schema changes must be reviewable, version-controlled, reversible.
- Existing dev databases (created under `ddl-auto=update`) must keep working without manual intervention.
- The migration path must run in tests against a real PostgreSQL container, not against Hibernate's interpretation of the entity model.
- Adding a JPA entity field without an accompanying migration must fail the build.
- No new licensed components.

## Considered options

1. **Stay on `ddl-auto=update`** — the status quo from ADR-0007.
2. **Switch to Liquibase** with a baseline of the current Axon schema (this change).
3. **Switch to Flyway** with the same baseline strategy.
4. **Hand-rolled SQL scripts run from a Job** — no migration tool.

## Decision

We chose **option 2**. See `openspec/changes/replace-ddlauto-with-liquibase/design.md` for the full set of decisions; the headline ones:

- **Liquibase over Flyway** — declarative YAML changelogs, stronger preconditions for the baseline, better DB-abstraction story for adopters who fork to non-PostgreSQL targets.
- **YAML changelogs** with a master file (`db.changelog-master.yaml`) and one numbered include per changeset (`0001-*.yaml`).
- **Baseline strategy**: the first changeset wraps a `pg_dump --schema-only` of the Axon-generated schema and is gated by a `not tableExists(domain_event_entry)` precondition with `onFail: MARK_RAN`. On a fresh database it executes; on a legacy database (where the tables already exist) it is recorded as applied without running. From that point on, both environments are in lockstep.
- **`ddl-auto: validate`** in every profile, including `application-test.yaml` (was `create-drop`). Tests now exercise the same DDL as production.
- **Liquibase Gradle plugin** wired in the `infrastructure` module for *author-time* tasks (`liquibaseStatus`, `liquibaseDiff`, `liquibaseValidate`). Runtime migrations are driven by Spring Boot's `LiquibaseAutoConfiguration` at startup — we do not invoke Liquibase from Gradle at deploy time.
- **CI guard** (`verifyMigrationCoverage` Gradle task, hooked into `check`): fails if `@Entity`-bearing class bytecode has changed and no new changelog file was added since the previous commit. Override marker `[no-migration]` in the commit message for non-persistent entity changes.

## Consequences

### Positive

- Schema state is fully captured in Git. `databasechangelog` is the audit trail.
- Tests run against the same DDL as production (the migration), eliminating an entire class of dev-prod drift bugs.
- Adding an entity field without a migration is a *build failure*, not a runtime mutation.
- The Helm / k8s deployment can ship a `pre-install / pre-upgrade` Job that runs `liquibase update` — this change unblocks that work.
- Adopters who fork the template have a working, opinionated migration story they can extend; they no longer have to introduce migration tooling as their first change.

### Negative / accepted trade-offs

- Liquibase adds ~200–500 ms to startup on a healthy DB (one query against `databasechangelog`). Negligible.
- The baseline is a frozen historical artefact. If the Axon schema changes (e.g. an Axon major upgrade), that is a new changeset, not an edit to `0001-*.yaml`.
- The CI guard is a heuristic. False positives (cosmetic entity change without persistence impact) are handled by the `[no-migration]` override marker. False negatives (forgot to add a migration) are caught by the integration test, which runs the full migration path against a Testcontainer.
- We pay a one-time cost regenerating the baseline if the Axon-shipped DDL ever drifts (we re-run `pg_dump` and replace the file). Documented procedure.

## Links

- [ADR-0007 — No Liquibase (superseded)](0007-no-liquibase.md)
- [ADR-0003 — Event sourcing on PostgreSQL](0003-event-sourcing-postgres-store.md) — defines what's in the database.
- [ADR-0008 — Spring Boot 4 baseline + BOM-first version management](0008-spring-boot-4-baseline.md) — Liquibase version is BOM-managed.
- [arc42 §2](../arc42/arc42.md#2-architecture-constraints) — Liquibase is now in the compatibility matrix.
- OpenSpec change folder: `openspec/changes/replace-ddlauto-with-liquibase/` (proposal, design, specs/database-migrations + platform-baseline modification, tasks).
