<div align="center">

# SkyStarter

**A Spring Boot 4 / Java 25 reference template for event-sourced services**

[![Java](https://img.shields.io/badge/Java-25-orange.svg?style=flat&logo=openjdk)](https://adoptium.net/temurin/releases/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-6DB33F?style=flat&logo=springboot)](https://spring.io/projects/spring-boot)
[![Axon Framework](https://img.shields.io/badge/Axon%20Framework-5.1.0-3D5AFE?style=flat)](https://www.axoniq.io/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15%2B-336791?style=flat&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![MongoDB](https://img.shields.io/badge/MongoDB-7%2B-47A248?style=flat&logo=mongodb&logoColor=white)](https://www.mongodb.com/)
[![Keycloak](https://img.shields.io/badge/Keycloak-OIDC-4D4D4D?style=flat&logo=keycloak)](https://www.keycloak.org/)
[![Liquibase](https://img.shields.io/badge/Liquibase-managed-2962FF?style=flat)](https://www.liquibase.org/)
[![Gradle](https://img.shields.io/badge/Gradle-9.4-02303A?style=flat&logo=gradle)](https://gradle.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg?style=flat)](LICENSE)
[![arc42](https://img.shields.io/badge/arc42-documented-informational?style=flat)](docs/architecture/arc42/arc42.md)
[![ADRs](https://img.shields.io/badge/ADRs-10-blueviolet?style=flat)](docs/architecture/decisions/)
[![OpenSpec](https://img.shields.io/badge/OpenSpec-spec--driven-teal?style=flat)](https://github.com/Fission-AI/OpenSpec)

</div>

---

SkyStarter is a fork-ready starter project demonstrating **CQRS + Event Sourcing** on **Spring Boot 4** with **hexagonal (Ports & Adapters)** module boundaries. It uses **Axon Framework 5** (Entity Model) over a **PostgreSQL** event store, materialises read-side projections into **MongoDB**, and authenticates clients against **Keycloak** via OIDC. Schema changes are managed by **Liquibase**.

Replace the `Sky` aggregate with your own domain and you have a production-shaped service in minutes.

## Documentation

| Topic | Where |
|---|---|
| 🏃 **Running locally** (prerequisites, profiles, IntelliJ run configs, Docker) | [Running guide](docs/running.md) |
| 🧪 **Testing strategy** (automated test suite + manual end-to-end procedure with curl) | [Testing strategy](docs/testing-strategy.md) |
| 📖 **OpenAPI / Swagger / Postman** | [API documentation](docs/openapi.md) |
| 🩺 **Health probes** (Actuator) | [Health probes](docs/probes.md) |
| 🗃️ **Database migrations** (Liquibase) | [Database migrations](docs/database-migrations.md) |
| 🛡️ **Code quality** (formatter, OWASP, dependency analysis, migration guard) | [Code quality](docs/code-quality.md) |
| 🔐 **Local Keycloak certificate** generation | [Certificate generation](docs/certificates.md) |
| 🏛️ **Architecture** — arc42, diagrams, decisions | [Architecture overview](docs/architecture/) |
| 🤖 **AI agents** (Claude Code, Kilo, OpenCode, Codex CLI; OpenSpec workflow) | [Agent tooling](docs/AGENT_TOOLING.md) |
| 📜 **Shared agent instructions** | [AGENTS.md](AGENTS.md) |

## Architecture at a glance

| Module | Responsibility |
|---|---|
| `domain` | Axon 5 Entity Model — aggregate state (`SkyAggregate` annotated `@EventSourced`), external command handlers (`SkyCommandHandlers`), commands, events with `@EventTag`. |
| `service` | Use-case orchestration over `CommandGateway` / `QueryGateway`. |
| `infrastructure` | Adapters — REST, MongoDB projection, MapStruct mappers, Keycloak OIDC, Liquibase, Axon configuration. |
| `app` | Spring Boot composition root. |

Strict inward dependency rule: `app → infrastructure → service → domain`. The full picture lives in [`docs/architecture/arc42/arc42.md`](docs/architecture/arc42/arc42.md), with C4-style diagrams under [`docs/architecture/diagrams/`](docs/architecture/diagrams/) and the decision history under [`docs/architecture/decisions/`](docs/architecture/decisions/).

## Quick start

```shell
# 1. Local PostgreSQL (port 5432, db 'starter') and MongoDB (port 27017) running.
# 2. Run the service:
./gradlew bootRun --args='--spring.profiles.active=local'

# 3. Service listens on http://localhost:7777 — try the Swagger UI:
#    http://localhost:7777/openapi/swagger-ui.html

# 4. Or run the full automated test suite (requires Docker for Testcontainers):
./gradlew clean build
```

For details on every step (including the test JWT for the `local` profile, Docker build, and IntelliJ setup), see [`docs/running.md`](docs/running.md). For a hands-on `curl`-driven walkthrough of the CRUD lifecycle plus inspection of the event store and projection, see [`docs/testing-strategy.md`](docs/testing-strategy.md).
