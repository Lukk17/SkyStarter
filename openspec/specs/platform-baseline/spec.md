# platform-baseline

## Purpose

Defines the platform version contract for the SkyStarter template: which Java, Spring Boot, Axon, BOM, and runtime versions the template stands on, what is pinned vs. delegated to a BOM, and how compatibility across environments is preserved across upgrades.

## Requirements

### Requirement: Java toolchain version

The build SHALL enforce a single Java toolchain version across all modules. The toolchain version MUST match the minimum version required by the Spring Boot baseline declared in this spec.

#### Scenario: Java 25 enforced for Spring Boot 4

- **WHEN** any module is compiled or any test is run
- **THEN** Gradle MUST resolve a Java 25 toolchain (Adoptium / Temurin)
- **AND** the build MUST fail fast if a developer's machine cannot provision a Java 25 JDK

#### Scenario: Single source of truth for the toolchain version

- **WHEN** the toolchain version needs to change
- **THEN** the change MUST be made in exactly one place (`build.gradle.kts` `projectJavaVersion` constant)
- **AND** no module's `build.gradle.kts` MAY override the toolchain version locally

### Requirement: Spring Boot baseline

The platform SHALL target Spring Boot 4.0.x as its baseline. The exact version MUST be declared in `gradle/libs.versions.toml` under a single version key (`springBoot`) and referenced everywhere via that key.

#### Scenario: Spring Boot version declared once

- **WHEN** the version catalog is inspected
- **THEN** there MUST be exactly one `springBoot` version key in `[versions]`
- **AND** every Spring Boot starter in `[libraries]` MUST resolve to that version through the Spring Boot BOM (no per-starter version pin)

#### Scenario: Compatible Spring Framework + Security versions

- **WHEN** the project is built
- **THEN** Spring Framework, Spring Security, and Spring Data versions MUST be those that the chosen Spring Boot 4 release pulls in
- **AND** no module MAY override these versions

### Requirement: BOM-first version management

The build SHALL prefer **BOM imports** over explicit version pins for any third-party library that publishes a BOM. Explicit version pins are allowed only for libraries that have no BOM, or whose BOM is not yet aligned with the platform baseline.

#### Scenario: Spring Boot BOM imported

- **WHEN** any subproject declares a Spring Boot–managed dependency (e.g. `spring-boot-starter-web`, `postgresql`, Jackson, JUnit)
- **THEN** the Spring Boot dependency-management plugin MUST resolve its version
- **AND** the version catalog entry for that library MUST NOT carry a `version.ref`

#### Scenario: Testcontainers BOM imported

- **WHEN** any test source set declares a Testcontainers module (`junit-jupiter`, `postgresql`, `mongodb`)
- **THEN** versions MUST resolve through the `org.testcontainers:testcontainers-bom` platform import
- **AND** no Testcontainers library entry in the catalog MAY have a `version.ref`

#### Scenario: Axon BOM imported

- **WHEN** Axon Framework dependencies are declared (`axon-spring-boot-starter`, `axon-modelling`, `axon-eventsourcing`, `axon-test`)
- **THEN** versions MUST resolve through the `org.axonframework:axon-bom` platform import
- **AND** no Axon library entry in the catalog MAY have a `version.ref`

#### Scenario: Library has no BOM

- **WHEN** a library publishes no BOM and no transitive BOM covers it (e.g. a standalone code generator)
- **THEN** the catalog MAY pin its version explicitly
- **AND** the catalog entry MUST carry an inline comment naming the BOM that should replace the pin in a future upgrade, if any candidate exists

### Requirement: Subset of pinned versions

The `[versions]` block in `gradle/libs.versions.toml` SHALL contain only:
1. Versions of BOMs themselves,
2. Versions of Gradle plugins (which cannot be BOM-managed),
3. Versions of libraries that no relevant BOM covers.

#### Scenario: Catalog audit

- **WHEN** the catalog is reviewed
- **THEN** every key in `[versions]` MUST fall into one of the three categories above
- **AND** any key that does NOT MUST be removed in favour of a BOM resolution

### Requirement: Per-environment compatibility matrix

The platform SHALL document the supported runtime versions for every external dependency in the deployment topology (PostgreSQL, MongoDB, Keycloak, Docker base image).

#### Scenario: Compatibility matrix exists and is current

- **WHEN** the architecture documentation is read (`docs/architecture/arc42/arc42.md` §2)
- **THEN** there MUST be a table listing the supported major versions of: JDK, PostgreSQL, MongoDB, Keycloak, container base image
- **AND** the table MUST be updated as part of any change that bumps the Spring Boot baseline

### Requirement: Aggregate framework alignment

The Axon Framework version SHALL be the latest GA release that is compatible with the chosen Spring Boot baseline at the time the upgrade is applied. The change MUST NOT proceed to apply if no Boot-compatible Axon release exists.

#### Scenario: Axon GA available

- **WHEN** the Spring Boot baseline is bumped
- **THEN** the upgrade MUST verify that an Axon release explicitly compatible with that Spring Boot version exists
- **AND** the verification MUST be recorded in the corresponding ADR

#### Scenario: Axon GA not yet available

- **WHEN** no Boot-4-compatible Axon release exists at the time of apply
- **THEN** the upgrade MUST be paused and the spec MUST flag this as a blocked state
- **AND** the team MUST NOT manually back-port Axon onto an unsupported Spring Boot

### Requirement: Build plugins compatibility

Gradle plugins (Spotless, Sonarqube, OWASP Dependency Check, Dependency Analysis, springdoc-openapi-gradle-plugin) SHALL be at versions verified against the Gradle wrapper and JDK toolchain in this spec.

#### Scenario: Plugin compatibility verified

- **WHEN** the build is run on a fresh checkout
- **THEN** all configured plugins MUST resolve and apply without deprecation warnings that would fail the build under `--warning-mode=fail`
- **AND** any plugin that is not yet compatible MUST be replaced or temporarily disabled with an explicit TODO comment

### Requirement: Container base image alignment

The Docker base image used by `Dockerfile` SHALL be the same JDK major version as the toolchain.

#### Scenario: Image and toolchain match

- **WHEN** the Docker image is built
- **THEN** the base image JDK major version MUST equal `projectJavaVersion` from the build script
- **AND** a mismatch MUST cause the image build to fail (e.g. via a runtime sanity check on `java -version`)
