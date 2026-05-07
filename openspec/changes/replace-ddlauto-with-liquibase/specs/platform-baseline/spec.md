## MODIFIED Requirements

### Requirement: Per-environment compatibility matrix

The platform SHALL document the supported runtime versions for every external dependency in the deployment topology (PostgreSQL, MongoDB, Keycloak, Docker base image, **Liquibase**).

#### Scenario: Compatibility matrix exists and is current

- **WHEN** the architecture documentation is read (`docs/architecture/arc42/arc42.md` §2)
- **THEN** there MUST be a table listing the supported major versions of: JDK, PostgreSQL, MongoDB, Keycloak, container base image, **Liquibase**
- **AND** the table MUST be updated as part of any change that bumps the Spring Boot baseline or introduces a new managed runtime dependency
