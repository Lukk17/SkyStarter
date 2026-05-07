## Why

Spring Boot 4.0 (GA late 2025) is the next-generation baseline: it requires Java 25, ships as the platform target for Spring Framework 7, and brings the API consolidations (`spring-boot-starter-restclient`, `spring-jdbc-client`, the rebuilt `spring-security-oauth2-jose`, etc.) that the rest of the ecosystem is now publishing against. SkyStarter exists to be a fork-ready template — staying on Boot 3.5.x condemns every fork to the same upgrade chore. We do it once, here, so downstream services start on the modern baseline.

A version bump on this scale is also the right moment to lean harder on **dependency BOMs**. The current `gradle/libs.versions.toml` pins many libraries with explicit versions (`postgres`, `mapstruct`, `gson`, `springDocOpenApi`, …) that the Spring Boot BOM, the Testcontainers BOM, and the Axon BOM can manage for us. Fewer pinned versions = fewer drift points = fewer fork merge conflicts.

## What Changes

- **BREAKING**: Java toolchain bumped from **21 → 25** (Spring Boot 4 minimum).
- **BREAKING**: Spring Boot **3.5.7 → 4.0.x**, Spring Framework **6.x → 7.x**, Spring Security **6.x → 7.x**.
- **BREAKING**: Jakarta EE baseline moves up; transitive APIs (Servlet, Validation, Persistence) shift accordingly. Any deprecated 3.x method our code calls must be replaced.
- **BREAKING**: Spring Security 7 default changes (e.g., `requestMatchers`, `authorizeHttpRequests` lambda DSL is mandatory; `Customizer` removals) — `SecurityConfig` and `LocalSecurityConfig` are reviewed and updated.
- Adopt platform BOMs to drop explicit version pins where Spring Boot / library BOMs already manage them:
  - **Spring Boot BOM** (already imported via `spring-dependency-management`) → drop explicit `postgres`, `gson` versions if the BOM manages them; otherwise keep the catalog entry but track the BOM-managed default.
  - **Testcontainers BOM** (`org.testcontainers:testcontainers-bom`) → manages `testcontainers-junit`, `testcontainers-postgres`, `testcontainers-mongodb` versions.
  - **MapStruct BOM** (if published) or single-version pin → `mapstruct` and `mapstruct-processor` aligned.
  - **Axon BOM** (`org.axonframework:axon-bom`) → manages `axon-spring-boot-starter`, `axon-modelling`, `axon-eventsourcing`, `axon-test` from one version key.
  - **springdoc-openapi BOM** if/when published; otherwise keep the single version pin.
- `gradle/libs.versions.toml` is restructured: BOMs go in a `[libraries]` section flagged as platform imports, version refs are removed from libraries that the BOMs cover, and the `[versions]` block shrinks to "things no BOM covers" + the BOM versions themselves.
- **BREAKING**: Axon Framework upgraded to the version compatible with Spring Boot 4 (Axon 4.10+ or 5.x line, whichever is GA at the time of apply). Aggregate / event-sourcing API changes are absorbed.
- Hibernate dialect (`ByteaEnforcedPostgresSQLDialect`) revalidated against the Hibernate version Spring Boot 4 ships with.
- `Dockerfile` base image bumped from JDK 21 → JDK 25 (Adoptium Temurin).
- `.run/` IntelliJ run configs and `README.md` prerequisites updated to Java 25.
- OWASP Dependency Check, Spotless, Sonarqube, and Dependency Analysis plugins bumped to their latest GA compatible with Gradle and JDK 25.

Non-goals for this change:
- No functional / API surface changes to `/v1/starter/**`.
- No new endpoints, no schema changes, no projection changes.
- No swap of any infrastructure component (PostgreSQL stays, MongoDB stays, Keycloak stays).

## Capabilities

### New Capabilities

- `platform-baseline`: Defines the Java + Spring Boot + Axon + library version contract for the template. Captures **which versions are pinned in code vs. delegated to a BOM**, the upgrade cadence rules, and the per-environment compatibility matrix. Lives at `openspec/specs/platform-baseline/spec.md` so future upgrades update one spec instead of scattering decisions across build files.

### Modified Capabilities

(None. This change introduces the platform-baseline spec for the first time; no existing specs are present in `openspec/specs/`.)

## Impact

- **Code**:
  - `gradle/libs.versions.toml` — restructured around BOMs.
  - All `*/build.gradle.kts` — `platform(...)` import additions; explicit version refs removed where a BOM covers them.
  - `build.gradle.kts` (root) — Java toolchain `21 → 25`.
  - `infrastructure/.../config/security/{SecurityConfig,LocalSecurityConfig}.java` — Spring Security 7 DSL adjustments.
  - `infrastructure/.../config/persistence/ByteaEnforcedPostgresSQLDialect.java` — re-verify against new Hibernate.
  - Possibly `SkyAggregate` and event/command classes if Axon API moves on the targeted version.
- **Build / CI**:
  - JDK 25 required on developer machines and CI runners.
  - Gradle wrapper bumped to a version compatible with JDK 25 (≥ 8.10).
  - OWASP Dependency Check baseline regenerated (different transitive set under Boot 4).
  - Sonarqube / Spotless plugin versions verified against Gradle ≥ 8.10.
- **Runtime**:
  - Container base image — JDK 25.
  - Memory profile may shift (G1 default tuning differs on 25); operators should re-profile.
- **Docs**:
  - `README.md` prerequisites section updated.
  - `docs/architecture/arc42/arc42.md` "Architecture constraints" table updated.
  - New ADR `0008-spring-boot-4-baseline.md` documenting the upgrade decision and BOM strategy.
- **Tests**:
  - All test modules recompile cleanly under JDK 25.
  - Spring Security test (`@WithMockUser`, `MockMvc` standalone) revalidated against Security 7.
  - Testcontainers images already managed via BOM — no version drift.

Risk: Axon Framework's release cadence relative to Spring Boot 4 GA. If the Axon BOM does not yet have a Boot-4-compatible release at apply time, this change is held until it does (no manual back-port).
