# database-migrations

## Purpose

Defines how schema changes are authored, versioned, applied, and verified for the JPA datasource. Liquibase is the chosen tool; this spec covers what makes a migration valid, how the build enforces it, and how the integration test confirms it before any deployment.

## Requirements

### Requirement: Migrations are the only path to schema change

Schema changes to the JPA datasource SHALL only happen through Liquibase changesets. `spring.jpa.hibernate.ddl-auto` MUST be set to `validate` (or stricter) in every non-development profile, and to `validate` in `application.yaml`.

#### Scenario: Application startup with no pending migrations

- **WHEN** the application starts and the database is at the latest changelog revision
- **THEN** Liquibase MUST report zero pending changes
- **AND** Hibernate MUST validate that the runtime entity model matches the schema
- **AND** the application MUST start normally

#### Scenario: Application startup with pending migrations

- **WHEN** the application starts and the database is behind the latest changelog revision
- **THEN** Liquibase MUST apply the pending changesets in order before any Spring bean depending on the datasource is initialised
- **AND** the application MUST start only after migrations complete successfully

#### Scenario: Schema drift detected by Hibernate at startup

- **WHEN** the application starts and the runtime entity model does not match the schema
- **THEN** the application MUST fail to start with an actionable Hibernate validation error
- **AND** the operator's response MUST be to author a new changeset (never to flip `ddl-auto` back to `update`)

### Requirement: Axon event-store schema is baselined

The Axon Framework's JPA event-store tables (`domainevententry`, `snapshotevententry`, `tokenentry`, `saga_entry`, `association_value_entry`) SHALL be captured as a Liquibase **baseline changeset** with a precondition that prevents re-execution on environments where those tables already exist.

#### Scenario: Fresh database

- **WHEN** Liquibase runs against an empty database
- **THEN** the baseline changeset MUST execute and create the Axon event-store tables
- **AND** the resulting schema MUST be byte-identical to what `ddl-auto=create` produced under the same Hibernate / Axon versions

#### Scenario: Existing database (legacy `ddl-auto=update` environment)

- **WHEN** Liquibase runs against a database that already contains the Axon event-store tables
- **THEN** the baseline changeset MUST be skipped via its precondition
- **AND** the changeset MUST be recorded as `EXECUTED` in `databasechangelog` so future runs treat it as applied

### Requirement: Changelog organisation

The changelog SHALL use the **YAML** format and a single master file that includes one changeset file per logical change, ordered numerically.

#### Scenario: Adding a new schema change

- **WHEN** a developer needs to add a column, table, or index
- **THEN** they MUST create a new file `db/changelog/NNNN-<short-slug>.yaml` with the next sequential `NNNN`
- **AND** they MUST add an `<include file="...">` line to `db.changelog-master.yaml`
- **AND** they MUST NOT modify any previously-committed changeset (changes to history are forbidden — superseding changesets are the only allowed mechanism)

### Requirement: Every changeset is reversible

Every changeset SHALL declare a `rollback` block. Liquibase's auto-rollback for trivially reversible operations (e.g. `addColumn`, `createTable`) is acceptable; for destructive changes (drop, rename, data migration), the rollback MUST be explicit.

#### Scenario: Trivially reversible change

- **WHEN** a changeset only adds tables, columns, or indexes
- **THEN** an explicit `rollback` block MAY be omitted
- **AND** Liquibase's auto-rollback MUST suffice

#### Scenario: Destructive or non-trivially reversible change

- **WHEN** a changeset drops, renames, or moves data
- **THEN** the changeset MUST include an explicit `rollback` block describing how to reverse the change
- **AND** if the change is genuinely irreversible, the `rollback` MUST contain a `<sql>` line that raises an exception with a descriptive message

### Requirement: Migrations are CI-verified before merge

The CI pipeline SHALL fail if either:
1. A push touches JPA entity classes without adding a corresponding changeset, OR
2. The cumulative changelog cannot be applied cleanly to a fresh PostgreSQL container.

#### Scenario: PR adds an entity field without a migration

- **WHEN** a pull request modifies a `@Entity` class to add or alter a persisted field, and no new changeset file is added
- **THEN** the CI guard MUST fail the build with a message naming the entity and recommending the next changelog filename
- **AND** the guard MUST NOT block PRs that include an explicit "no-schema-change" override marker (e.g. a `[no-migration]` token in the commit message) for cases where the entity change is non-persistent (e.g. adding `@Transient` field)

#### Scenario: Changelog applied against a fresh container

- **WHEN** CI starts a clean PostgreSQL container and runs `liquibase update`
- **THEN** every changeset MUST apply without error
- **AND** the resulting schema MUST satisfy Hibernate's `validate` mode against the runtime entity model

### Requirement: Liquibase runs in integration tests

The end-to-end integration test (`SkyEndToEndIT`) SHALL exercise the Liquibase migration path against a fresh PostgreSQL Testcontainer.

#### Scenario: Integration test startup

- **WHEN** `SkyEndToEndIT` starts the Spring application context
- **THEN** Liquibase MUST run against the Testcontainer-provided PostgreSQL instance
- **AND** the baseline changeset MUST execute (because the container is empty)
- **AND** the test MUST proceed with the schema produced by Liquibase, not by Hibernate

### Requirement: Migrations are idempotent under retries

A failed migration run SHALL leave the database in a state where retrying yields the correct result.

#### Scenario: Liquibase fails mid-changeset

- **WHEN** a changeset throws an error while applying
- **THEN** Liquibase MUST mark the changeset as failed in `databasechangelog`
- **AND** the next run MUST retry the same changeset (not skip it)
- **AND** the changeset author MUST verify their SQL is wrapped in an implicit transaction (Liquibase default) or is otherwise idempotent

### Requirement: Local development convenience tasks

The Gradle build SHALL expose Liquibase tasks for local diff-based authoring against a developer's PostgreSQL.

#### Scenario: Generating a diff

- **WHEN** a developer runs `./gradlew :infrastructure:liquibaseDiff` with a local PostgreSQL configured
- **THEN** Liquibase MUST emit a candidate changeset describing the differences between the runtime entity model and the live schema
- **AND** the developer MUST review the candidate, edit if necessary, and commit it manually (the task MUST NOT auto-add files to the changelog)
