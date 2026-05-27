# Database migrations (Liquibase)

The JPA datasource (PostgreSQL) is managed exclusively by [Liquibase](https://www.liquibase.org/). `spring.jpa.hibernate.ddl-auto` is fixed at `validate` in every profile — Liquibase is the **only** path to schema change. ADR-0009 records the rationale; ADR-0007 (the previous "no migrations" decision) is superseded.

## Layout

```
infrastructure/src/main/resources/db/changelog/
├── db.changelog-master.yaml                       # include order
├── 0001-axon-event-store-baseline.yaml            # changeset wrapper + precondition
├── 0001-axon-event-store-baseline.sql             # DDL captured under Axon 4.9
└── 0002-axon-5-event-store-migration.yaml         # Axon-4 → Axon-5 schema delta
```

The baseline (`0001-...`) is **frozen** — never edited. Per ADR-0009, future schema changes stack as new numbered changesets that build on top.

## Adding a migration

1. Copy the highest-numbered file under `db/changelog/` to a new file `NNNN-<short-slug>.yaml` (next sequential 4-digit number).
2. Edit the new file with the change (`addColumn`, `createTable`, `addIndex`, `modifyDataType`, `dropColumn`, etc.) using [Liquibase's YAML reference](https://docs.liquibase.com/concepts/changelogs/yaml-format.html).
3. Append an `include` line to `db.changelog-master.yaml`:
   ```yaml
   - include:
       file: db/changelog/NNNN-<short-slug>.yaml
       relativeToChangelogFile: false
   ```
4. Validate the changelog locally against your Postgres:
   ```shell
   ./gradlew :infrastructure:liquibaseValidate \
       -Pliquibase.url=jdbc:postgresql://localhost:5432/starter \
       -Pliquibase.username=postgres \
       -Pliquibase.password=local
   ```
5. Run the integration test to confirm the changelog applies cleanly to a fresh PostgreSQL Testcontainer and that Hibernate `validate` is happy:
   ```shell
   ./gradlew :app:test
   ```

## Changes to `@Entity` classes

Schema change goes through Liquibase only (`spring.jpa.hibernate.ddl-auto: validate` everywhere), so any `@Entity` change that alters the persisted shape needs an accompanying changeset under `infrastructure/src/main/resources/db/changelog/`. The integration test boots against a real PostgreSQL (Testcontainers) with `validate`, so a schema/entity mismatch fails the build there.

## Working tasks

Provided by the [`org.liquibase.gradle`](https://github.com/liquibase/liquibase-gradle-plugin) plugin (wired in `infrastructure/build.gradle.kts`):

| Task | Purpose |
|---|---|
| `:infrastructure:liquibaseStatus` | Show which changesets are pending. |
| `:infrastructure:liquibaseValidate` | Sanity-check changelog syntax. |
| `:infrastructure:liquibaseUpdate` | Apply pending changesets. |
| `:infrastructure:liquibaseDiff` | Compare current schema to changelog (advanced). |

All take Postgres connection details via `-Pliquibase.url`, `-Pliquibase.username`, `-Pliquibase.password`.
