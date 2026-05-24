# SkyStarter — arc42 architecture documentation

> Template version: arc42 v8.2 (single-file form). Diagrams live in
> [`../diagrams/`](../diagrams/), ADRs live in [`../decisions/`](../decisions/).

---

## 1. Introduction and goals

SkyStarter is a **bootstrap template** for new backend services that use
**CQRS + Event Sourcing** on top of **Spring Boot 3 / Java 21** with
**hexagonal (Ports & Adapters)** module boundaries. It is not a product on its
own — its purpose is to give a new service a clean, opinionated starting point
where the trade-offs have already been made and the wiring is already done.

### 1.1 Requirements overview

| # | Requirement | Notes |
|---|---|---|
| F-1 | Expose a sample REST API for a `Sky` aggregate (CRUD-style: create / get / update / delete). | The "Sky" name is a placeholder domain that downstream services replace. |
| F-2 | Persist all writes as immutable events (event sourcing). | Axon Framework manages the event store. |
| F-3 | Serve reads from a separate, query-optimised projection. | MongoDB document collection rebuilt from events via a tracking processor. |
| F-4 | Authenticate all endpoints with Keycloak-issued JWTs (OIDC). | Realm roles → Spring authorities; method-level `@PreAuthorize`. |
| F-5 | Support running offline / locally without a live Keycloak. | `local` profile decodes JWTs without signature verification. |
| NF-1 | Java 21, Spring Boot 3.5.x, Gradle Kotlin DSL multi-module. | Toolchain enforced; UTF-8 mandated. |
| NF-2 | Strict inward dependency rule between modules. | `app → infrastructure → service → domain`; domain depends on nothing. |
| NF-3 | Horizontal scaling friendly: stateless service + Axon tracking processors with segments. | `initial-segment-count: 8` rounds to 16 segments. |
| NF-4 | Observability via Spring Boot Actuator (health, readiness, liveness probes). | Probes intentionally `permitAll`. |
| NF-5 | Secure-by-default: no SQL/CSRF/XSS/auth bypass; secrets never in code. | OWASP Dependency Check fails build on CVSS ≥ 7. |

### 1.2 Quality goals

| Priority | Goal | What it means in practice |
|---|---|---|
| 1 | **Replicability** | A new team can fork this repo, rename the aggregate, and ship a service in days, not weeks. |
| 2 | **Testability** | Every layer is independently testable. Domain has no Spring-context startup cost. |
| 3 | **Auditability** | Event sourcing + ADRs make the *why* of every state change discoverable. |
| 4 | **Operational readiness** | Probes, structured logs, and segment tuning in place out of the box. |
| 5 | **Security** | OIDC by default; method-level role checks; hardcoded credentials forbidden. |

### 1.3 Stakeholders

| Role | Concern |
|---|---|
| Service owner / new team | Wants a fast, correct starting point. |
| Platform team | Wants services that fit the deployment, monitoring, and IAM stack. |
| Security | Wants OIDC, RBAC, and dependency hygiene to be the default. |
| Maintainer (Lukk17) | Wants the template to evolve without diverging across forks. |

---

## 2. Architecture constraints

| Constraint | Source | Impact |
|---|---|---|
| Java 25 (Adoptium) | `build.gradle.kts` toolchain | All bytecode at JDK 25. Virtual threads enabled. Foojay resolver provisions the JDK on dev/CI machines that don't have it. |
| Spring Boot 4.0.x | `gradle/libs.versions.toml` | Jakarta namespaces; Spring Framework 7 / Spring Security 7; Jackson 3 (`tools.jackson.*`); persistence/web/test packages reorganised. |
| Axon Framework 5.1.0 (no Axon Server) | `application.yaml` (`axon.axonserver.enabled=false`) | Event store sits on the primary JPA/PostgreSQL datasource. Resolved via `axon-framework-bom`; Spring Boot integration under groupId `org.axonframework.extensions.spring`. Entity Model — aggregate is a state class with `@EventSourced(idType, tagKey)`; command handlers external; events tagged with `@EventTag`. |
| MongoDB for projections | `application.yaml` | Read model is a separate datastore — denormalised by intent. |
| Keycloak as IdP | `SecurityConfig` | Realm roles in `realm_access.roles` claim. Spring Security 7 also injects a `FactorGrantedAuthority` automatically. |
| Gradle multi-module + version catalog | `settings.gradle.kts`, `libs.versions.toml` | Dependencies declared once, used many. Versions resolved through BOMs (Spring Boot, Spring Cloud, Spring Modulith, Testcontainers, Axon) wherever possible. |
| Hexagonal module boundary | `domain` has no Spring web/data deps | Inward-only — enforced by build. ArchUnit available for automated verification. |

### Per-environment compatibility matrix

| Component | Required version |
|---|---|
| JDK | 25 (Adoptium / Temurin) |
| Spring Boot | 4.0.6 |
| Spring Framework | 7.x (transitive) |
| Spring Security | 7.x (transitive) |
| Spring Cloud BOM | 2025.1.1 |
| Spring Modulith BOM | 2.0.6 |
| Axon Framework | **5.1.0** (`axon-framework-bom`); Spring Boot starter under `org.axonframework.extensions.spring` |
| PostgreSQL | 15+ |
| MongoDB | 7.0+ |
| Keycloak | 26+ (any OIDC / RFC 8414–compliant IdP works) |
| Container base image | `eclipse-temurin:25-jdk` (build), `eclipse-temurin:25-jre-alpine` (runtime) |
| Gradle wrapper | 9.4.0 (≥ 8.10 required for JDK 25) |
| Liquibase | resolved via `spring-boot-bom` (no explicit pin); changelog at `infrastructure/src/main/resources/db/changelog/db.changelog-master.yaml` |

---

## 3. System scope and context

### 3.1 Business context

```
[ External client ]  --HTTPS/JWT-->  [ SkyStarter ]  --reads/writes-->  [ PostgreSQL (events) ]
                                          |                          \-->  [ MongoDB (projections) ]
                                          \--validates token-->  [ Keycloak ]
```

See [diagram: business context](../diagrams/01-business-context.md).

### 3.2 Technical context

| Channel | Protocol | Purpose |
|---|---|---|
| Inbound REST | HTTPS, JSON, Bearer JWT | Public API under `/v1/starter/**` |
| Actuator | HTTP, JSON | Probes (`/actuator/health/{liveness,readiness}`) |
| OIDC | HTTPS, JWKS | Token validation against Keycloak |
| Event store | JDBC | PostgreSQL — append-only event log (snapshots deferred, ADR-0011) |
| Read model | MongoDB wire protocol | Document collection `skyProjections` |

---

## 4. Solution strategy

| Decision | Rationale | ADR |
|---|---|---|
| Hexagonal layout (`domain → service → infrastructure → app`). | Keeps business logic uncontaminated by frameworks; forces ports. | [ADR-0001](../decisions/0001-hexagonal-module-layout.md) |
| CQRS via Axon `CommandGateway` / `QueryGateway`. | Reads and writes have different shapes and scaling needs. | [ADR-0002](../decisions/0002-cqrs-with-axon.md) |
| Event sourcing on PostgreSQL. | Audit + replayable projections; PostgreSQL is operational baseline. | [ADR-0003](../decisions/0003-event-sourcing-postgres-store.md) |
| MongoDB for read projections. | Document model fits arbitrary query views; rebuilds from events. | [ADR-0004](../decisions/0004-mongodb-read-projections.md) |
| Keycloak / OIDC for AuthN; method-security for AuthZ. | Standards-based; integrates with existing platform IdP. | [ADR-0005](../decisions/0005-keycloak-oidc-auth.md) |
| Tracking event processor with segmented parallelism. | Horizontal scaling without sacrificing per-aggregate ordering. | [ADR-0006](../decisions/0006-tracking-processor-segmentation.md) |
| Liquibase removed; Hibernate `ddl-auto=update` + custom dialect. | Axon's schema is stable; schema migrations are not a value-add for a template. | [ADR-0007](../decisions/0007-no-liquibase.md) |

---

## 5. Building block view

### 5.1 Whitebox: SkyStarter (level 1)

```
+-------------------- SkyStarter ----------------------+
|                                                      |
|   app  ──▶  infrastructure  ──▶  service  ──▶  domain |
|                                                      |
+------------------------------------------------------+
```

| Module | Responsibility | Depends on |
|---|---|---|
| `domain` | Aggregates, events, commands/queries, domain model, ports. **No Spring, no DB.** | (Axon API only) |
| `service` | Use-case orchestration: implements the domain ports using Axon gateways. | `domain` |
| `infrastructure` | Adapters: REST controllers, MongoDB repository, MapStruct mappers, security, OpenAPI, exception handler, Axon config. | `domain`, `service` |
| `app` | Spring Boot composition root + `main`. | `infrastructure` |

See [diagram: container view](../diagrams/02-container-view.md) and [diagram: module dependencies](../diagrams/03-module-dependencies.md).

### 5.2 Domain (level 2)

| Element | Type | Notes |
|---|---|---|
| `SkyAggregate` | Axon 5 `@EventSourced(idType=UUID.class, tagKey="skyId")` | Pure state class. `@EntityCreator` no-arg constructor + `@EventSourcingHandler` methods that evolve state when prior events are replayed. Command handlers live in `SkyCommandHandlers`. Snapshots: not wired — deferred, see ADR-0011. |
| `SkyCommandHandlers` | `@Component` with three `@CommandHandler` methods | External command handlers (Axon 5 idiom). Each method takes the command, the loaded entity via `@InjectEntity(idProperty="skyId") SkyAggregate state`, and an `EventAppender`. Validates and emits events through the appender. |
| `Sky` | Read-model record | DTO returned from queries. |
| `SkyValidator` | Domain service | Currently: name not blank. Extend per business rule. |
| `SkyCreatedEvent` / `SkyUpdatedEvent` / `SkyDeletedEvent` | Axon events | Past-tense, immutable, serialised by Jackson. |
| `SkyCommandService` / `SkyQueryService` | Ports (interfaces) | Implemented in the `service` module; consumed by controllers. |
| `SkyNotFoundException` | Domain exception | Mapped to HTTP 404 in `GlobalExceptionHandler`. |

### 5.3 Infrastructure (level 2)

| Element | Notes |
|---|---|
| `StarterController` | `/v1/starter` REST surface; method-secured with `hasAnyRole('USER','ADMIN')`. Returns `CompletableFuture<...>`. |
| `GlobalExceptionHandler` | Maps domain & framework exceptions to RFC 9457 `ProblemDetail` (`application/problem+json`, `urn:skystarter:error:*` types); unwraps `CompletionException`. |
| `SkyProjection` | `@Component` event handler bound to the `sky-projection-processor` (Axon 5 dropped `@ProcessingGroup` — the processor is configured in `application.yaml`); on `SkyCreatedEvent`/`SkyUpdatedEvent`/`SkyDeletedEvent` it writes to MongoDB. Also handles `FindSkyByIdQuery`. |
| `SkyEntity` + `SkyMongoRepository` | Document model and Spring Data Mongo repository. |
| `SkyApiMapper` / `SkyPersistenceMapper` | MapStruct mappers (compile-time generated). |
| `SecurityConfig` / `LocalSecurityConfig` | Profile-split security: real OIDC vs offline JWT decoder. |
| `KeycloakAuthenticationConverter` | Maps `realm_access.roles` claim → Spring `GrantedAuthority`. |
| `AxonConfig` | Placeholder Axon configuration class; snapshots deferred (ADR-0011). |
| `PersistenceConfiguration` | Resolves "strict repository mode" by directing JPA scanner away from Mongo repo package. |
| `ByteaEnforcedPostgresSQLDialect` | Forces `BLOB → bytea` so Axon event payloads land in `bytea` columns on PostgreSQL 15+. |

---

## 6. Runtime view

### 6.1 Create-Sky flow

```
client → POST /v1/starter
     → StarterController.createSky
     → SkyCommandServicePrimary
        → CommandGateway.send(CreateSkyCommand)
            → SkyAggregate(CreateSkyCommand)            # validates
              → AggregateLifecycle.apply(SkyCreatedEvent)
                  ↳ Axon writes event to PostgreSQL
                  ↳ tracking processor delivers to SkyProjection
                       → SkyMongoRepository.save(SkyEntity)
     ← 201 Created  body=<UUID>
```

See [diagram: create-sky sequence](../diagrams/04-create-sky-sequence.md).

### 6.2 Get-Sky flow

```
client → GET /v1/starter/{id}
     → StarterController.getSky
     → SkyQueryServicePrimary
        → QueryGateway.query(FindSkyByIdQuery, Sky.class)
           → SkyProjection.handle(FindSkyByIdQuery)
              → SkyMongoRepository.findById(id)
                ↳ SkyNotFoundException if absent
     ← 200 OK | 404 Not Found
```

See [diagram: get-sky sequence](../diagrams/05-get-sky-sequence.md).

### 6.3 Eventual consistency note

The write side (PostgreSQL event store) and the read side (MongoDB projection) are connected by a **tracking event processor**. A `GET` issued immediately after a `POST` may return 404 until the projection catches up. Production callers must tolerate this. Tests use Awaitility to wait for projection convergence.

---

## 7. Deployment view

| Environment | Profile | DB | IdP |
|---|---|---|---|
| Local IDE | `local` | local PostgreSQL + MongoDB | offline JWT decoder |
| Local Docker | `local,docker` | `host.docker.internal` PostgreSQL + MongoDB | offline JWT decoder |
| Test (CI) | `test` | Testcontainers (PostgreSQL 15 + MongoDB 7) | OAuth2 auto-config disabled, `TestSecurityConfig` injects a stub chain |
| Production | (no profile) | managed PostgreSQL + MongoDB | Keycloak via `issuer-uri` |

Deployment artefact: a single fat JAR (`sky-starter.jar`, see `app/build.gradle.kts`) packaged into a Docker image (`Dockerfile` at repo root, JDK 21 base, custom certificate handling for corporate Keycloak TLS).

See [diagram: deployment view](../diagrams/06-deployment-view.md).

---

## 8. Cross-cutting concepts

### 8.1 Domain modelling

- Aggregates are reconstituted from events (`@EventSourcingHandler`); state outside aggregates is read-only projection.
- Validation lives in domain services (`SkyValidator`) called from the aggregate constructor / handlers — never in controllers, never in projections.

### 8.2 API design

- Resource path: `/v1/starter/{skyId}` — versioned at v1 from day one (Spring Boot 4 native path versioning, kept verbatim by `StringApiVersionParser`).
- POST returns `201 Created` with a `CreateSkyResponse` body (`{ "skyId": ... }`) and a `Location` header; PUT/DELETE return `204 No Content`.
- Errors use RFC 9457 `ProblemDetail` (`application/problem+json`) with `urn:skystarter:error:*` type URIs.
- Bean Validation on request DTOs (`@NotBlank`, `@Size`); failures yield a `400` ProblemDetail of type `urn:skystarter:error:validation` with an `errors` object mapping field → message.

### 8.3 Security

- `@EnableMethodSecurity` plus `@PreAuthorize` per endpoint; no security at the URL level beyond the catch-all `authenticated()`.
- Roles come from Keycloak's `realm_access.roles` claim, mapped 1:1 by `KeycloakAuthenticationConverter`.
- CSRF/CORS disabled — this is a JSON API behind an API gateway, not a browser session app.
- Local profile uses offline JWT decode; signature is **not verified** — never use this profile in production.

### 8.4 Observability

- Actuator health probes split into `liveness` and `readiness` (Kubernetes-friendly).
- Structured console logs with explicit pattern; SQL/Mongo query logging gated by package levels.

### 8.5 Schema management

- **Liquibase** owns every schema mutation against the JPA datasource. `spring.jpa.hibernate.ddl-auto` is `validate` in every profile (no exceptions).
- Changelog: `infrastructure/src/main/resources/db/changelog/db.changelog-master.yaml` includes one `NNNN-<slug>.yaml` per change, ordered numerically.
- Adding a migration: copy the next-numbered file, add an `<include>` line to the master, run `./gradlew :infrastructure:liquibaseValidate` locally with a Postgres URL, commit.
- The `verifyMigrationCoverage` Gradle task fails the build when `@Entity`-bearing class bytecode changes without a new changelog file. Override marker for non-persistent entity changes: `[no-migration]` in the commit message.

### 8.6 Testing strategy

| Level | Where | Tools | Goal |
|---|---|---|---|
| Domain unit | `domain/src/test` | JUnit 5, AssertJ, **Axon `AggregateTestFixture`** | Behaviour of aggregate / validator without Spring. |
| Service unit | `service/src/test` | JUnit 5, Mockito | Command/query gateways are dispatched correctly. |
| Adapter unit | `infrastructure/src/test` | JUnit 5, Mockito, MapStruct factory | Projection, mappers, exception handler, JWT converter. |
| Web slice | `infrastructure/src/test` | MockMvc standalone | Controller wiring + validation + status codes. |
| Integration | `app/src/test` (`*IT`) | Testcontainers (PostgreSQL + MongoDB), Awaitility, `@WithMockUser` | Full CQRS round-trip incl. eventual consistency. |
| Smoke | `app/src/test` | `@SpringBootTest` | Application context loads. |

---

## 9. Architectural decisions

All decisions live under [`../decisions/`](../decisions/) as MADR files. Currently:

- [0001 — Hexagonal module layout](../decisions/0001-hexagonal-module-layout.md)
- [0002 — CQRS with Axon Framework](../decisions/0002-cqrs-with-axon.md)
- [0003 — Event sourcing on PostgreSQL](../decisions/0003-event-sourcing-postgres-store.md)
- [0004 — MongoDB for read projections](../decisions/0004-mongodb-read-projections.md)
- [0005 — Keycloak OIDC for authentication](../decisions/0005-keycloak-oidc-auth.md)
- [0006 — Tracking processor with segmentation](../decisions/0006-tracking-processor-segmentation.md)
- [0007 — No Liquibase](../decisions/0007-no-liquibase.md)
- [0008 — Spring Boot 4 baseline + BOM-first version management](../decisions/0008-spring-boot-4-baseline.md)
- [0009 — Liquibase + Axon event-store baseline](../decisions/0009-liquibase-axon-baseline.md) (supersedes 0007)
- [0010 — Upgrade to Axon Framework 5 (Entity Model)](../decisions/0010-upgrade-to-axon-5.md)

---

## 10. Quality requirements

| ID | Requirement | Scenario | Verification |
|---|---|---|---|
| Q-1 | Domain layer has no framework dependency. | A test importing `SkyAggregateTest` compiles and runs without Spring on the classpath of the `domain` test source set. | `domain/build.gradle.kts` deps; `SkyAggregateTest` runs via `AggregateTestFixture`. |
| Q-2 | Read-after-write converges within 10 s under default config. | `SkyEndToEndIT.fullCqrsLifecycle_*` succeeds within Awaitility's 15 s budget. | CI runs the IT. |
| Q-3 | Unauthenticated requests are rejected at every protected endpoint. | `SkyEndToEndIT.unauthenticated_isRejected` returns 401. | CI runs the IT. |
| Q-4 | Validation errors return a structured 400 with field-level detail. | `code=VALIDATION_ERROR`, `details` is a map of field → message. | `StarterControllerTest.createSky_blankName_returns400Validation`. |
| Q-5 | No build-failing CVE in dependencies. | `./gradlew dependencyCheckAnalyze` exits clean. | `dependencyCheck` task in CI gate. |

---

## 11. Risks and technical debt

| Risk | Impact | Mitigation |
|---|---|---|
| Eventual-consistency window widening under load. | Stale reads after write; user confusion. | Tune segment count and event processor batch size; add Axon metrics; consider a materialised "in-flight write" cache for read-your-writes if needed. |
| Snapshots not wired under Axon 5. | Axon 5.1.0's `SnapshotStore` SPI is `@Internal` and only attachable via the manual `declarative(...)` builder, which is incompatible with `@EventSourced` auto-detection (ADR-0011). Aggregates with very long event streams replay all events on load. | Demo workloads aren't affected. Reassess when Axon ships a stable, auto-detection-compatible snapshot store; see ADR-0011. |
| Baseline DDL drift on Axon major upgrades. | New `<NNNN>-axon-X-event-store-migration.yaml` changesets stack on top of the baseline (see `0002-axon-5-event-store-migration.yaml` for the precedent). | Per ADR-0009, the baseline is never edited; the integration test catches schema drift via Hibernate `validate`. |
| Local profile decodes JWT without verification. | Catastrophic if accidentally enabled in prod. | Profile name `local`; production `SecurityConfig` is `!local & !test`. Document explicitly. |
| Single Mongo collection (`skyProjections`) for the only read view. | Adding a second read shape requires a new projection processor. | Keep one collection per query shape; rebuild from events when introducing a new view. |

---

## 12. Glossary

| Term | Meaning |
|---|---|
| **Aggregate** | DDD/Axon: consistency boundary; state reconstituted from a stream of events keyed by aggregate id. |
| **Command** | An imperative request to change state; targets exactly one aggregate. |
| **Event** | An immutable past-tense fact emitted by an aggregate; the source of truth. |
| **Query** | A read request; served from a projection, not from the aggregate. |
| **Projection** | A read-optimised model rebuilt from events. Lives in MongoDB here. |
| **Tracking processor** | Axon: a stream-following consumer that reads events from the store with checkpoints; supports segmentation for parallelism. |
| **Snapshot** | A serialised aggregate state taken every N events to short-circuit replay on load. |
| **JWKS** | JSON Web Key Set — the IdP's public keys used to verify JWT signatures. |
