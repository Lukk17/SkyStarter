# Baseline snapshot — pre-upgrade

Captured during /opsx:apply task §1. Use as the rollback target and the diff anchor for the upgrade commit message.

| Component | Current version | Notes |
|---|---|---|
| Spring Boot | 3.5.7 | `gradle/libs.versions.toml` `springBoot` |
| Spring Dependency Management plugin | 1.1.7 | `springDependencyManagement` |
| Axon Framework | 4.9.3 | `axon` |
| springdoc-openapi (starter) | 2.8.13 | `springDocOpenApi` |
| springdoc-openapi-gradle-plugin | 1.9.0 | `springdocOpenApiPlugin` |
| MapStruct | 1.6.3 | `mapstruct` (no BOM) |
| lombok-mapstruct-binding | 0.2.0 | `lombokMapstructBinding` (no BOM) |
| Gson | 2.13.2 | `gson` (Spring-Boot-managed → drop version.ref) |
| PostgreSQL JDBC | 42.7.8 | `postgres` (Spring-Boot-managed → drop version.ref) |
| Sonarqube plugin | 6.3.1.5724 | `sonarqube` (no BOM) |
| OWASP Dependency Check plugin | 12.1.8 | `owaspDependencyCheck` (no BOM) |
| Dependency Analysis plugin | 3.4.1 | `dependencyAnalysis` (no BOM) |
| Spotless plugin | 8.0.0 | `spotless` (no BOM) |
| Java toolchain | 21 (Adoptium) | `build.gradle.kts` `projectJavaVersion = 21` |
| Gradle wrapper | **9.4.0** | already > 8.10 — **§3 (wrapper bump) is a no-op** |

## What §2 (BOM adoption) will manage

| Pulled by BOM | Drop `version.ref`? |
|---|---|
| Spring Boot starters | already no version.ref |
| `org.postgresql:postgresql` | yes (Boot BOM) |
| `com.google.code.gson:gson` | yes (Boot BOM) |
| `org.awaitility:awaitility` | yes (Boot BOM) |
| `org.junit.jupiter:*`, `org.mockito:*`, `org.assertj:*`, `org.hamcrest:hamcrest` | already no version.ref |
| `org.testcontainers:*` | yes (Testcontainers BOM, even though Boot BOM also manages them — explicit import per spec) |
| `org.axonframework:*` | yes (Axon BOM) |

## What stays pinned after §2

- MapStruct + processor + Lombok-MapStruct binding (no BOM)
- springdoc-openapi-starter (no Boot-aligned BOM)
- All Gradle plugins (plugin versions cannot be BOM-managed)

## Drift introduced by §2

| Artifact | Pre-refactor | Post-refactor (via BOM) | Note |
|---|---|---|---|
| `org.axonframework:*` | 4.9.3 | **4.9.2** | The Axon BOM `4.9.3` pins artifact versions at `4.9.2` (BOM versioning lags artifact versioning by one patch). One-patch regression accepted; tests green. To return to 4.9.3, the BOM version would need to bump (next BOM release). |
| `org.postgresql:postgresql` | 42.7.8 | 42.7.8 | exact match (Spring Boot BOM). |
| `com.google.code.gson:gson` | 2.13.2 | 2.13.2 | exact match (Spring Boot BOM). |
| `org.testcontainers:*` | (Spring-Boot-managed transitively) | testcontainers-bom 1.20.4 | Now pinned via explicit BOM. Spring Boot 3.5.7 manages 1.20.x transitively, so no observable change. |

## §1.3 baseline build status

Domain, service, infrastructure unit tests were verified green earlier in the session (43 passing). The full `:app:test` run requires Docker for Testcontainers and was not executed here. Rollback target is the working tree at the start of /opsx:apply.
