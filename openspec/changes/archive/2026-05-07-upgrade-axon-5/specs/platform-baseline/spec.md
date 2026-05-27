## MODIFIED Requirements

### Requirement: Aggregate framework alignment

The Axon Framework version SHALL be the latest GA release that is compatible with the chosen Spring Boot baseline at the time the upgrade is applied. The change MUST NOT proceed to apply if no Boot-compatible Axon release exists.

The currently aligned version is **Axon Framework 5.1.0**, resolved via the **`org.axonframework:axon-framework-bom`** BOM (note: this BOM artifact is distinct from the legacy `org.axonframework:axon-bom` artifact used for the 4.x line; the rename is part of the Axon 5 release).

The Spring Boot integration (`axon-spring-boot-starter`, `axon-spring-boot-autoconfigure`, `axon-spring-boot-starter-test`, `axon-spring`) is published under the **`org.axonframework.extensions.spring`** groupId, separately from the core framework artifacts which remain under `org.axonframework`.

#### Scenario: Axon GA available

- **WHEN** the Spring Boot baseline is bumped
- **THEN** the upgrade MUST verify that an Axon release explicitly compatible with that Spring Boot version exists
- **AND** the verification MUST be recorded in the corresponding ADR

#### Scenario: Axon GA not yet available

- **WHEN** no Boot-compatible Axon release exists at the time of apply
- **THEN** the upgrade MUST be paused and the spec MUST flag this as a blocked state
- **AND** the team MUST NOT manually back-port Axon onto an unsupported Spring Boot

#### Scenario: BOM and starter coordinates

- **WHEN** the version catalog (`gradle/libs.versions.toml`) is read
- **THEN** the `axon-bom` library entry MUST resolve to `org.axonframework:axon-framework-bom` at the version recorded in `[versions]` `axonBom`
- **AND** the `axon-spring-boot-starter` library entry MUST resolve to `org.axonframework.extensions.spring:axon-spring-boot-starter` (no version pin — managed by the BOM)
- **AND** core framework artifacts (`axon-modelling`, `axon-eventsourcing`, `axon-test`, `axon-messaging`) MUST resolve under `org.axonframework` with no version pin

### Requirement: Per-environment compatibility matrix

The platform SHALL document the supported runtime versions for every external dependency in the deployment topology (PostgreSQL, MongoDB, Keycloak, Docker base image, Liquibase, **Axon Framework**).

#### Scenario: Compatibility matrix exists and is current

- **WHEN** the architecture documentation is read (`docs/architecture/arc42/arc42.md` §2)
- **THEN** there MUST be a table listing the supported major versions of: JDK, PostgreSQL, MongoDB, Keycloak, container base image, Liquibase, **Axon Framework (with the BOM artifact id and starter groupId noted)**
- **AND** the table MUST be updated as part of any change that bumps the Spring Boot baseline, the Axon Framework version, or introduces a new managed runtime dependency
