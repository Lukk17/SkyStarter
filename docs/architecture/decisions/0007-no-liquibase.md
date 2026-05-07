# 0007 — No Liquibase; rely on Hibernate `ddl-auto=update` + custom dialect

- **Status:** Superseded by [ADR-0009](0009-liquibase-axon-baseline.md) on 2026-05-04. Body preserved below for historical context.
- **Date:** 2025-11-15
- **Deciders:** Project maintainer
- **Tags:** persistence, migrations, template-scope

## Context and problem statement

The earlier history of this repo included Liquibase changelogs to manage the PostgreSQL schema for Axon's event store and snapshots. As a *template*, that machinery added overhead disproportionate to its value:

- Axon's event-store DDL is stable across patch versions and bundled with the framework.
- Domain projections do not live in PostgreSQL — they live in MongoDB (no schema migrations).
- A new team forking this template must drop their own schema migration tool in anyway.

Carrying Liquibase made the template look like it had a migration story; it didn't, beyond Axon's own tables.

## Decision drivers

- Keep template surface small.
- Don't pretend to solve a problem the downstream team will solve their own way.
- Make local startup boring: `ddl-auto=update` against a dev Postgres just works.

## Considered options

1. **Keep Liquibase** with changelogs for Axon's event store and snapshots.
2. **Switch to Flyway**.
3. **Drop migration tooling**, rely on `ddl-auto=update`, document it as a template-only choice.

## Decision

We chose **option 3** for the duration of the template. `application.yaml` sets `spring.jpa.hibernate.ddl-auto: update`, the custom `ByteaEnforcedPostgresSQLDialect` ensures Axon's BLOB payloads land in PostgreSQL `bytea` columns, and there is no Liquibase or Flyway on the classpath.

This ADR is intentionally narrow in scope: it is the right call **for a template that is meant to be forked and customised**. It is the wrong call for any service that has reached production.

## Consequences

### Positive

- Local dev starts with one fewer moving part.
- No migration changelog drift between the template and forks.
- `ByteaEnforcedPostgresSQLDialect` is the only SQL-shaping artefact, and it is unit-testable.

### Negative / accepted trade-offs

- `ddl-auto=update` is unsuitable for production — the supersede-trigger for this ADR is "this code is being promoted past template status".
- Adopting teams must add Liquibase/Flyway as their first change. We document this as a starter-task.
- No baseline schema is captured — when migrations are introduced later, a snapshot of the existing schema must be taken first.

## Links

- [arc42 §11](../arc42/arc42.md#11-risks-and-technical-debt) — listed as a known technical debt item.
- [ADR-0003](0003-event-sourcing-postgres-store.md) — defines what is in the Postgres database.
