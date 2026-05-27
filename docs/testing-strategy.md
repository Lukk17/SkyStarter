# Testing strategy

This template ships with a layered test suite plus a manual end-to-end procedure. Run the automated tests on every change; run the manual procedure once after a fresh clone, after major upgrades, or before tagging a release.

## Automated tests

| Layer | Where | Tools | Goal |
|---|---|---|---|
| Domain unit | `domain/src/test` | JUnit 5, Mockito, AssertJ | `SkyValidator`, `SkyAggregate` (state evolution + handler behaviour via mocked `EventAppender`), `SkyCommandHandlers`. |
| Service unit | `service/src/test` | JUnit 5, Mockito | `SkyCommandServicePrimary`, `SkyQueryServicePrimary` — verify gateway dispatch + `CompletableFuture` adaptation. |
| Infrastructure unit / slice | `infrastructure/src/test` | JUnit 5, Mockito, MockMvc standalone, MapStruct factory | `SkyProjection`, mappers (API + persistence), `GlobalExceptionHandler`, `KeycloakAuthenticationConverter`, `StarterController`. |
| Integration | `app/src/test` (`*IT`) | Testcontainers (PostgreSQL + MongoDB), Awaitility, Spring Security test post-processors | Full CQRS round-trip — Liquibase migrates a fresh container, Axon 5 wires, command/event/projection chain converges; security-filter rejection of unauthenticated requests covered separately by `SecurityFilterIT`. |

### Run

```shell
# Unit + slice tests only (fast, no Docker)
./gradlew :domain:test :service:test :infrastructure:test

# Including the integration test (Docker required for Testcontainers)
./gradlew :app:test

# Everything, plus coverage gate + integration tests
./gradlew check
```

Reports land at `*/build/reports/tests/test/index.html`.

## Manual end-to-end test

Use this when you want to verify the application behaves correctly with **real** PostgreSQL, MongoDB, and Keycloak running on your machine — not the disposable Testcontainers ones.

### Prerequisites

| Component | Default coordinates | Notes |
|---|---|---|
| **PostgreSQL 15+** | `localhost:5432`, db `starter`, user `postgres`, password `local` | Configured in `application.yaml`. Create the `starter` database before first run. |
| **MongoDB 7+** | `localhost:27017`, db `starter` | The driver auto-creates the `skyProjections` collection on first write. |
| **Keycloak 26+** *(optional with `local` profile)* | `https://keycloak:9443/`, realm with a `pharmaApp-client` client | Required if you want to mint real JWTs. Under the `local` profile the offline decoder accepts any well-formed JWT. |
| **JDK 25** | auto-provisioned by Gradle Foojay | No manual install. |
| **Docker** | optional for this manual flow | Only the automated integration test needs it. |

### One-time setup

1. **Create the Postgres database** (if it doesn't already exist):
   ```shell
   psql -U postgres -h localhost -c 'CREATE DATABASE starter'
   ```
2. **Start MongoDB** (if not already running):
   ```shell
   mongod --dbpath /your/data/dir
   ```
   *or* run via Docker:
   ```shell
   docker run -d --name local-mongo -p 27017:27017 mongo:7
   ```
3. **(Optional) Start Keycloak** at `https://keycloak:9443/`. If you skip this, use the test JWT from [`docs/postman/`](postman/) with the `local` profile.
4. **Apply migrations** (the app does this automatically on startup, but you can pre-apply for inspection):
   ```shell
   ./gradlew :infrastructure:liquibaseUpdate \
       -Pliquibase.url=jdbc:postgresql://localhost:5432/starter \
       -Pliquibase.username=postgres -Pliquibase.password=local
   ```

### Run the application

```shell
./gradlew bootRun --args='--spring.profiles.active=local'
```

Wait for `Started AppApplication in N seconds`. The service is now listening on `http://localhost:7777`.

### Smoke test

Replace `<JWT>` with a Bearer token (the example in [`docs/running.md`](running.md) works under the `local` profile).

#### 1. Health check (no auth required)

```shell
curl -s http://localhost:7777/actuator/health/readiness
# Expected: {"status":"UP"}
```

#### 2. Create a Sky

```shell
curl -i -X POST http://localhost:7777/v1/starter \
    -H "Authorization: Bearer <JWT>" \
    -H "Content-Type: application/json" \
    -d '{"name":"Andromeda"}'
# Expected: HTTP/1.1 201 Created  body: "<uuid>"
```

Capture the returned UUID — call it `$ID`.

#### 3. Fetch the Sky (verifies the projection caught up)

```shell
curl -s http://localhost:7777/v1/starter/$ID \
    -H "Authorization: Bearer <JWT>"
# Expected: {"skyId":"<uuid>","name":"Andromeda","status":"CREATED"}
```

If you get `404 Not Found`, the projection processor hasn't caught up yet — wait one second and retry. Eventual consistency between the event store (Postgres) and the read model (Mongo) is on the order of a few hundred ms in development.

#### 4. Update the Sky

```shell
curl -i -X PUT http://localhost:7777/v1/starter/$ID \
    -H "Authorization: Bearer <JWT>" \
    -H "Content-Type: application/json" \
    -d '{"name":"Andromeda-2"}'
# Expected: HTTP/1.1 200 OK
```

Re-fetch — `name` should now be `Andromeda-2`.

#### 5. Delete the Sky

```shell
curl -i -X DELETE http://localhost:7777/v1/starter/$ID \
    -H "Authorization: Bearer <JWT>"
# Expected: HTTP/1.1 200 OK
```

A subsequent GET should return **404 NOT_FOUND** with body `{"code":"NOT_FOUND",...}`.

#### 6. Validation error

```shell
curl -i -X POST http://localhost:7777/v1/starter \
    -H "Authorization: Bearer <JWT>" \
    -H "Content-Type: application/json" \
    -d '{"name":"  "}'
# Expected: HTTP/1.1 400 Bad Request
# body: {"code":"VALIDATION_ERROR","details":{"name":"Sky name must not be blank"}}
```

#### 7. Unauthenticated request

```shell
curl -i http://localhost:7777/v1/starter/$ID
# Expected: HTTP/1.1 401 (or 403 — Spring Security 7 default)
```

### Inspecting the data

After the lifecycle above, inspect the stores directly to verify the model.

**PostgreSQL — event store**:
```shell
psql -U postgres -h localhost -d starter -c \
    'SELECT identifier, type, aggregate_identifier, aggregate_sequence_number FROM aggregate_event_entry ORDER BY global_index'
```

You should see three rows for your aggregate: `SkyCreatedEvent` (seq 0), `SkyUpdatedEvent` (seq 1), `SkyDeletedEvent` (seq 2). Note `aggregate_event_entry` is Axon 5's table — under Axon 4 the table name was `domain_event_entry`.

**MongoDB — projection**:
```shell
mongosh starter --eval 'db.skyProjections.find({}).pretty()'
```

After the delete, the document should be gone. Before the delete, it reflects the latest known state.

**Liquibase changelog**:
```shell
psql -U postgres -h localhost -d starter -c \
    'SELECT id, author, exectype, dateexecuted FROM databasechangelog ORDER BY orderexecuted'
```

You should see two rows: `0001-axon-event-store-baseline` (`MARK_RAN` if you have an existing dev DB, `EXECUTED` on a fresh one) and `0002-axon-5-event-store-migration` (`EXECUTED`).

### Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `connection refused` on Postgres | DB not running or wrong port. | Start Postgres on 5432 (or override `spring.datasource.url`). |
| `Schema validation: missing table [...]` at startup | A new entity was added without a Liquibase changeset. | Add a `NNNN-...yaml` file (see [`docs/database-migrations.md`](database-migrations.md)). |
| `Schema validation: missing sequence [...]` | Hibernate 7 expects a per-table sequence that the changeset didn't create. | Add `CREATE SEQUENCE` to the changeset. The Axon 5 event-store uses the literal hyphenated name `"aggregate-event-global-index-sequence"` (quoted). |
| `An Authentication object was not found in the SecurityContext` | A request that should be authenticated isn't. Under the `local` profile this almost always means a missing or malformed `Authorization` header. | Use the test JWT from `docs/postman/`. |
| `MissingRepositoryException: No repository was registered for the given entity type [SkyAggregate] with id type [UUID]` | Axon 5's `@EventSourced(idType = UUID.class)` stereotype is missing or the lookup didn't pick up the bean. | Verify `SkyAggregate` is annotated `@EventSourced(idType = UUID.class, tagKey = "skyId")`. |
| Projection lags / `GET` returns 404 right after `POST` | Pooled event processor hasn't caught up. | Wait briefly. Tune `axon.eventhandling.processors.sky-projection-processor.initial-segment-count` for higher parallelism. |
