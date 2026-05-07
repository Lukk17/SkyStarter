## SkyStarter

---

### Table of Contents

- [Prerequisites](#prerequisites)
- [Running](#running)
    - [Local](#local)
    - [Run Configuration](#run-configuration)
    - [Terminal Commands](#terminal-commands)
    - [Docker](#docker)
    - [JDK Setup](#jdk-setup)
- [Postman](#postman)
- [Open Api](#open-api)
    - [Generating OpenApi spec](#generating-openapi-spec)
- [Probes](#probes-actuator)
- [Code Formatter](#code-formatter)
    - [Line Separator](#line-separator)
- [OWASP Dependency Check](#owasp-dependency-check)
- [Dependency Analysis](#dependency-analysis)
- [Architecture Overview](#architecture-overview)
    - [Module Structure & Dependencies](#module-structure--dependencies)
    - [The Three Models: API, Domain, and Entity](#the-three-models-api-domain-and-entity)
- [Axon Framework, CQRS & Event Sourcing](#axon-framework-cqrs--event-sourcing)
    - [Core Concepts](#core-concepts)
    - [How Axon Works in This Project](#how-axon-works-in-this-project)
    - [Database Usage](#database-usage)
- [AI Agents](#ai-agents)

---

## Database migrations

Schema changes go through Liquibase. To add one:

1. Copy the highest-numbered changeset under `infrastructure/src/main/resources/db/changelog/` to a new file `NNNN-<short-slug>.yaml` (next sequential 4-digit number).
2. Edit the new file with the change (`addColumn`, `createTable`, etc. — see Liquibase YAML reference).
3. Add `- include: { file: db/changelog/NNNN-<short-slug>.yaml, relativeToChangelogFile: false }` to `db.changelog-master.yaml`.
4. Validate locally:
   ```shell
   ./gradlew :infrastructure:liquibaseValidate \
     -Pliquibase.url=jdbc:postgresql://localhost:5432/starter \
     -Pliquibase.username=postgres -Pliquibase.password=local
   ```
5. Run the integration test (`./gradlew :app:test`) to confirm the changelog applies cleanly to a fresh PostgreSQL Testcontainer and that Hibernate `validate` is happy.

`spring.jpa.hibernate.ddl-auto` is fixed at `validate` in every profile — Liquibase is the only path to schema change. The build also enforces this: `verifyMigrationCoverage` fails CI if a JPA `@Entity` class changes without a corresponding new changelog file. For non-persistent entity changes (refactor, `@Transient` field), include `[no-migration]` in the commit message.

## AI Agents

For instructions on how to use AI coding agents (Claude Code, Kilo Code, OpenCode, Codex CLI) with this project — including skills, the OpenSpec workflow, and tooling setup — see [`docs/AGENT_TOOLING.md`](docs/AGENT_TOOLING.md). Shared agent instructions live in the root [`AGENTS.md`](AGENTS.md).

---

## Prerequisites

Java version: `25`

For Docker:
- docker installed
- keycloak running and available under `https://keycloak:9443/`

---

## Running

If the service is run directly (not in Spring Cloud) endpoints which need Principal data require header:  
`Authorization`  
with an example value:

```
Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJfMlJudkN3M3g0dHhFWk1nOElZcVoyZFh1RHpkRERTNUdYN2ZENWg5a1ZBIn0.eyJleHAiOjE3MzI4MjUwNjgsImlhdCI6MTczMjgyNDc2OCwiYXV0aF90aW1lIjoxNzMyODIwMDQ5LCJqdGkiOiI5ZTY3MWMxMS04ZmFhLTRlZDgtYTNmYy1hNzI3NjlhNzIyY2IiLCJpc3MiOiJodHRwczovL2tleWNsb2FrOjk0NDMvcmVhbG1zL3BoYXJtYSIsImF1ZCI6ImFjY291bnQiLCJzdWIiOiI1ZGExNWQyZC0yYzBjLTQ2MWEtOTlhOS1iNDQ5ZDllYzExYzgiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJwaGFybWFBcHAtY2xpZW50Iiwic2lkIjoiNThkZGRjN2YtMTk2ZC00ODRhLWJiMzctNWRjOWM0YjUzNWVlIiwiYWNyIjoiMCIsImFsbG93ZWQtb3JpZ2lucyI6WyJodHRwOi8vbG9jYWxob3N0Ojg4ODgiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbIlJPTEVfVVNFUiIsIm9mZmxpbmVfYWNjZXNzIiwidW1hX2F1dGhvcml6YXRpb24iLCJkZWZhdWx0LXJvbGVzLXBoYXJtYSJdfSwicmVzb3VyY2VfYWNjZXNzIjp7ImFjY291bnQiOnsicm9sZXMiOlsibWFuYWdlLWFjY291bnQiLCJtYW5hZ2UtYWNjb3VudC1saW5rcyIsInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoib3BlbmlkIHByb2ZpbGUgZW1haWwiLCJlbWFpbF92ZXJpZmllZCI6ZmFsc2UsIm5hbWUiOiJMdWtrIFRlc3QiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJsdWtrIiwiZ2l2ZW5fbmFtZSI6Ikx1a2siLCJmYW1pbHlfbmFtZSI6IlRlc3QiLCJlbWFpbCI6Imx1a2tAdGVzdC5jb20ifQ.CBSZsNjbKvNMXt8_MY0aMBsK7_ify3gAdRCsNDgrnpqP1vhFP4OkD71cmP2wBOO7QJIBxOXLWzGcApkshqGJVsFBmJ7cfCi65Dyi1SCugGSWjr9b3cYxBiTu4S46DvM7G-Bdagj-LfWrZlUWpketSZtQWmSzyR-cBYn9v8J45ONNYR03EAJiW_Y4i6yhMm_ywv5Z7vgZX1v5PorRCxQlHM1Q6tyG940Jqvz1NDlyo7blYORmDSmu8eie_hBvWQrTlRLgBkh4eZhrwvmQJusHeRF7E3JPp_lHF66p0ohRHJ6E7mTr1YE8RiEfU8ke0XXU1FCKEgEitEzBeDQ4rPoi0g
```

### Local

To run locally as separate application, it requires to use `local` spring profile.   
Which can be added to the Gradle run command  
as arg:

```shell
--args='--spring.profiles.active=local'
```

added as JVM argument:

```shell
-Dspring.profiles.active=local
```

or setup as env variable:

```shell
SPRING_PROFILES_ACTIVE=local
```

### Run configuration

In `./run/` folder there are configured ready to use run configurations for IntelliJ IDEA.

There are configuration for:

- local

### Terminal commands

```shell
./gradlew bootRun --args='--spring.profiles.active=local'
```

or

```shell
./gradlew bootRun -Dspring.profiles.active=local
```

### Docker

Remember to change docker tags !

Build Dockerfile (terminal in project root)

```shell
  docker build -t sky-starter:latest . 
```

without cache:

```shell
  docker build --no-cache -t sky-starter:latest .
```

Run it:

```shell
  docker run -d --name sky-starter -p 7777:7777 sky-starter:latest --add-host="keycloak.test:host-gateway"
```

`--add-host="keycloak.test:host-gateway"` - flag adds an entry to the container's internal hosts file (/etc/hosts),  
not the host machine's (Windows) hosts file. It maps the hostname keycloak to the special Docker keyword host-gateway,   
which resolves to the host machine's IP address on the virtual Docker network (this is not 127.0.0.1).  
This allows the container to connect to the host-run Keycloak service while still using the correct keycloak hostname for certificate validation,  
all without using the host's (e.g., Windows) hosts file.

### JDK Setup

This project uses Gradle Toolchains to ensure a consistent development environment. The required JDK version (Java 21)
is specified in the build.gradle.kts file.

If the correct JDK is not found on your local machine, Gradle will automatically download it using the Foojay Toolchain
Resolver. No manual JDK installation or JAVA_HOME configuration is needed to build the project.

JDK Download Location
The downloaded JDK will be stored in the Gradle user home directory:  
Windows:

```
C:\Users\<YourUsername>\.gradle\jdks\
```

macOS/Linux:

```
~/.gradle/jdks/
```

---

## Postman

collection (v2.1) is exported in:

[docs](./docs/postman/)

---

## Open API

UI:

```
http://localhost:7777/openapi/swagger-ui.html
```

Json docs:

```
http://localhost:7777/openapi/v3/api-docs
```

Yaml docs:

```
http://localhost:7777/openapi/v3/api-docs.yaml
```

config:

```
http://localhost:7777/openapi/v3/api-docs/swagger-config
```

### Generating OpenApi spec

```shell
    ./gradlew generateOpenApiDocs
```

View spec:
[openapi.yaml](./docs/api/openapi.yaml)

---

## Probes (Actuator)

Endpoints (GET):

```
http://localhost:7777/actuator
```

```
http://localhost:7777/actuator/info
```

```
http://localhost:7777/actuator/health
```

```
http://localhost:7777/actuator/health/{path}
```

where  
`{path}` - is a path from `/actuator/health` endpoint

example

```
http://localhost:7777/actuator/health/readiness
```

---

## Code formatter

Default for IntelliJ IDEA is used, exported can be found in:

[DefaultFormatting.xml](./DefaultFormatting.xml)

### Line separator

Use Unix line separator due to possible complication with Docker and deployment when using Windows CRLF  
To change in Intellij:

`Settings` → `Editor` → `Code Style` : `Line separator` - `Unix and macOS (\n)`

Change in git on windows:

```
git config --global core.autocrlf input
```

on Linux/macOs it can be disabled:

```
git config --global core.autocrlf false
```

Anyway a project has this setup in `.gitattribute` file.

---

## OWASP Dependency Check

Check dependencies for OWASP CVE security vulnerabilities

```shell
./gradlew dependencyCheckAggregate
```

View report:
[dependency-check-report.html](./build/reports/dependency-check-report.html)

For faster and unblocked check, request a free api key at:
https://nvd.nist.gov/developers/request-an-api-key

then add it in:
[gradle.properties](./gradle.properties)

```properties
nvdApiKey=<api-key>
```

---

## Dependency Analysis

Analyze dependencies graph

```shell
./gradlew buildHealth
```

View report:
[build-health-report.txt](./build/reports/dependency-analysis/build-health-report.txt)

---

## Architecture Overview

This project is built upon a modern software architecture combining several powerful patterns:  
- **Hexagonal Architecture (Ports and Adapters)**, 
- **CQRS (Command Query Responsibility Segregation)**, 
- **Event Sourcing**,
- **Event-Driven Architecture**. 
- This combination creates a system that is scalable, maintainable, and resilient.

The core idea is to separate the application's business logic from external concerns like databases, user interfaces, or messaging systems.

### Module Structure & Dependencies

The project is structured into distinct modules, each with a clear responsibility.  
The dependency rule is strict: dependencies only point inwards towards the `domain`.

```
[application] -> [infrastructure] 
[infrastructure] -> [service]
[infrastructure] -> [domain]
[service] -> [domain]
```

*   `domain`: The heart of the application. It contains the core business logic, rules, and state.
    *   **Aggregates**: Business objects that enforce consistency rules (e.g., `SkyAggregate`).
    *   **Domain Events**: Represent facts or things that have happened in the past (e.g., `SkyCreatedEvent`).
    *   **Commands & Queries**: Definitions of the operations the application can perform.
    *   **Domain Models**: Rich business models.
    *   **Service Interfaces**: Defines the contracts for application services.
    *   This module has **zero** external dependencies on frameworks or infrastructure details. The inclusion of Axon annotations (`@AggregateIdentifier`, `@CommandHandler`) is a pragmatic choice, tightly coupling the aggregate to the CQRS pattern implementation provided by the framework, which is acceptable.


* `service`: Contains the application's use cases. It orchestrates the flow of data between the outside world and the domain.
    *   **Service**: Implementation of services to orchestrate the flow of data.
  

* `infrastructure`: Contains the implementation details and integrations with external systems.
    *   **Projections**: Read-side models and the logic to build them (e.g., `SkyProjection`).
    *   **Repositories**: Implementations for persisting data (e.g., `SkyMongoRepository`).
    *   **Configuration**: Framework and technology-specific setup (e.g., Axon configuration).
    *   **Controllers**: Exposes REST endpoints.
    *   **DTOs (Data Transfer Objects)**: Models for API requests and responses. This layer is responsible for validating input and mapping it to application-layer Commands and Queries.


*   `application`: The entry point of the application.

### The Three Models: API, Domain, and Entity

To maintain separation of concerns, we use three distinct types of models:

1.  **API Models (DTOs)**: Plain data structures used in the `api` layer for requests and responses.  
    They are tailored to the needs of the client and can include validation annotations.
2.  **Domain Models (`Aggregate`)**: The rich business objects in the `domain` layer.  
    They contain both state and the logic that operates on that state. They are persistence-ignorant.
3.  **Entity Models (`@Document`)**: The persistence models in the `infrastructure` layer (e.g., `SkyEntity`).  
    They are designed to map cleanly to a database table or collection and are used by projections.

**Mappers** (`SkyPersistenceMapper`) are crucial for converting between these models,  
ensuring that concerns from one layer (like database annotations) do not leak into another (like the domain).

---

## Axon Framework, CQRS & Event Sourcing

This project uses the **Axon Framework** to implement CQRS and Event Sourcing.

### Core Concepts

*   **Command**: An object representing an intent to change the state of the system.  
    Commands are imperative and are handled by exactly one handler.
*   **Query**: An object representing a request for data.  
    Queries are descriptive, do not change state, and can be handled by multiple handlers.
*   **Aggregate**: A core domain object that encapsulates state and business logic.  
    It processes Commands to validate them and, if successful, produces Events.  
    Aggregate is defined in the `domain` module because it is the business logic.
*   **Event**: A record of something that has happened in the past.  
    Events are the single source of truth.
*   **Projection**: A read-side model built by listening to a stream of events.  
    The `SkyProjection` class listens for `SkyCreatedEvent`, `SkyUpdatedEvent`, etc.,  
    and builds a stateful view (`SkyEntity`) optimized for querying.  
    This lives in the `infrastructure` layer because it's a read-side implementation detail, dependent on a specific database (MongoDB).

### How Axon Works in This Project

#### Command
1.  **Command Dispatch**: An API controller receives a request (DTO), maps it to a **Command** object, and sends it to the system using the `CommandGateway`.
2.  **Command Handling**: The Command is routed to the appropriate **Aggregate**.  
    The Aggregate's command handler contains the business logic to validate the command.
3.  **Event Sourcing**: If the command is valid, the Aggregate applies one or more **Events**.  
    It does not change its state directly. Instead, it has a separate event sourcing handler (e.g., a method annotated with `@EventSourcingHandler`) that applies the event to its state. Axon then persists this event in the **Event Store**.
4.  **No Infrastructure Adapter for Aggregates**: There is no need for a custom repository or adapter in the `infrastructure` layer for our aggregates.  
    Axon's `EventSourcingRepository` (which is configured automatically) takes care of this. It knows how to save an aggregate by appending its new events to the Event Store and how to load an aggregate by replaying its entire event history.
5.  **Event Handling (Projections)**: The event is published on an event bus. **Projections** (`SkyProjection`) and other event handlers listen for these events.
6.  **Building the Read Model**: The `SkyProjection` receives the event in its `@EventHandler` method and updates its own data store (MongoDB) to reflect the change.  
    This creates a denormalized, query-optimized view of the data.

#### Query
1. **Query Dispatch**: When a client requests data, the API controller sends a **Query** object via the `QueryGateway`.
2. **Query Handling**: The Query is routed to the `SkyProjection`'s `@QueryHandler` method, which fetches the data directly from its fast, optimized MongoDB collection and returns it.

### Database Usage

This architecture uses two distinct types of databases for different purposes:

1.  **Event Store (Write Database)**:
    * **Purpose**: Stores the immutable, append-only log of all domain events. This is the single source of truth for the application's state.
    *   **Technology**: PostgreSQL, as configured in `application.yaml`. Axon creates and manages its own tables:
        *   `domain_event_entry`: The main table storing the serialized events, aggregate identifier, sequence number, and metadata.
        *   `snapshot_event_entry`: Stores periodic snapshots of aggregates to speed up loading (avoids replaying thousands of events).
        *   `token_entry`: Used by event processors (like our projection) to track which events they have already handled. This allows them to resume after a shutdown.
    *   **Justification**: Leverages existing infrastructure, mature operational tooling, and cost-efficiency of managed cloud services.
        Using `EventStoreDB` (which works similar to Axon server event store part) will be much more costly but resolve a pulling problem.
    *   **Known Trade-off**: This stack uses a "pull" model. Event Processors poll the JDBC store.

2. **Query/Read Database**:
    *   **Purpose**: Stores the denormalized, projected read models. It is optimized for fast queries.
    *   **Technology**: In this project, **MongoDB** is used.  
        The `SkyProjection` writes to a `skyProjections` collection (as defined in `SkyEntity`).
    *   **Justification**: Provides a high-performance, schema-flexible document store, ideal for persisting and querying denormalized read-side projections.

### Message Bus

There are two popular options for inter-services communication with Axon:
* Kafka
* Axon Server

Axon Server provides an excellent all-in-one solution (a "push"-based gRPC event store and a command/event bus).  
Its main advantage is that it only requires `axon-server-connector` dependency.  
Axon Framework finds it and automatically configures the Event Store, Command Bus, Event Bus, and Query Bus to use Axon Server

**Asynchronous API Design** 
Controllers are designed to be fully asynchronous, returning an HTTP 202 Accepted immediately after firing a command.   
This means Axon Server's biggest feature—automating the complex request-reply logic for commands—provides no benefit to here.

**Unavoidable Rehydration** 
Axon Server's "intelligent" load balancing (sticky routing) provides a minor optimization by routing commands to pods that might have an aggregate in their in-memory cache.  
For the vast majority of our use cases (especially Sagas and "cold" aggregates), rehydration from the database is unavoidable anyway.  
The performance benefit is therefore minimal.

**Required Kafka Configuration**
- **Manual Topic Setup**  
  Kafka topics need to be provisioned and managed for both Commands and Events.

- **Command Routing**  
  Aggregate services must be configured as Kafka consumers in the same `group.id` to ensure each command is processed by only one instance.

- **Event Publishing**  
  Services must be configured to publish all events to the shared event topic, which Sagas and Projections consume.

---

#### Localhost certificates generation

Generate a self-signed certificate using openssl and `localhost.cnf`  
Terminal in PharmacyCloud project root:
   ```shell
      openssl req -x509 -nodes -days 3650 -key ./certificates/localhost/localhostDomain.key -out ./certificates/localhost/localhostDomain.crt -config ./certificates/localhost/localhost.cnf -extensions req_ext
   ```
In file `localhost.cnf` all required information for certificate generation

`localhostDomain.crt  localhostDomain.key` files should be created