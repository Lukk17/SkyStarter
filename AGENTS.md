# AGENTS.md

This file provides shared instructions to all AI coding agents working in this repository (Claude Code, Kilo Code, OpenCode, Codex CLI). Standards and skills are imported from [agent-standards](https://github.com/Lukk17/agent-standards).

## Skills

This project includes agent skills in `.agents/skills/`. Invoke relevant skills before starting implementation work. Examples:

- `/code-reviewer` before reviewing code
- `/security-review` before auditing for vulnerabilities
- `/coding-standards` before writing new code
- `/tdd-workflow` before adding features or fixing bugs

Slash commands may appear as `/name` or `/name.md` in your agent's autocomplete — use whichever your agent shows.

## Working With Agents

All supported agents read this `AGENTS.md` from the project root and auto-discover skills from `.agents/skills/`. Start your agent from the project root:

- **Claude Code** — run `claude`. Reads `.claude/CLAUDE.md`, which imports this file.
- **Kilo Code** — reads `AGENTS.md` automatically. Optional `kilo.jsonc` for extra config.
- **OpenCode** — reads `AGENTS.md` automatically. Optional `opencode.json` at project root.
- **Codex CLI** — run `codex`. Reads `AGENTS.md` automatically. Global settings in `~/.codex/config.toml`.

## OpenSpec Workflow

This project uses [OpenSpec](https://github.com/Fission-AI/OpenSpec) for spec-driven development. Specs and changes live under `openspec/`.

The full lifecycle (run inside your agent shell):

1. **Propose a change** — agent generates proposal, design, and `tasks.md` under `openspec/changes/`:
   ```text
   /opsx:propose add dark mode support
   ```
2. **Apply the code** — after reviewing/editing `tasks.md`, agent implements and checks off tasks:
   ```text
   /opsx:apply
   ```
3. **Verify and refine** — pass back logs or bug reports to refine:
   ```text
   /opsx:verify The toggle button is invisible on mobile. Fix it.
   ```
4. **Archive** — once tested, merge delta specs into `openspec/specs/` and archive the change folder:
   ```text
   /opsx:archive
   ```

Some agents render commands as `/opsx-propose.md` instead of `/opsx:propose` — both work; use what appears in your autocomplete.

Use multiline prompts when you need to include logs or detailed context with a command.

## What This Repo Is

SkyStarter is a Spring Boot 4 backend service (Java 25, Gradle Kotlin DSL multi-module) demonstrating an event-sourced "Sky" domain. It exposes REST endpoints, persists events via Axon Framework 5 (Entity Model), serves reads from MongoDB projections, and integrates with Keycloak for OIDC-based authentication. The repo is a reference / starter template for CQRS + Event Sourcing on Spring Boot — used by the maintainer (Lukk17) to bootstrap new services and exercise hexagonal architecture patterns. Group `com.revdevs.pharmacy`, root project `sky-starter`.

## Architecture

- **Stack**: Java 25 (Adoptium, Foojay-resolved toolchain), Spring Boot 4.0.x, Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3 (`tools.jackson.*`), Gradle Kotlin DSL with version catalog, Lombok, JUnit Platform.
- **Build**: Multi-module Gradle build — `domain`, `service`, `infrastructure`, `app` (see `settings.gradle.kts`). BOM-first version management — Spring Boot, Spring Cloud, Spring Modulith, Testcontainers, Axon Framework BOMs imported via `platform(...)` in root subprojects. Static analysis via Spotless (auto tasks disabled), SonarQube, OWASP Dependency Check (fail on CVSS ≥ 7), and Gradle Dependency Analysis. JaCoCo applied to all subprojects.
- **Patterns**: Hexagonal (Ports & Adapters) + CQRS + Event Sourcing + Event-Driven. Strict inward dependency rule: `app → infrastructure → service → domain`.
  - `domain`: Axon 5 entity model (`SkyAggregate` annotated `@EventSourced(idType, tagKey)` — pure state class), `SkyCommandHandlers` (external `@CommandHandler` methods receiving `@InjectEntity` + `EventAppender`), domain events (with `@EventTag` on the id field), commands, queries. Spring is `compileOnly` and confined to the `@EventSourced` stereotype that the Axon-Spring bridge requires.
  - `service`: use-case orchestration over Axon's `CommandGateway` / `QueryGateway` (in their Axon-5 `org.axonframework.messaging.*.gateway` packages). Public ports stay `CompletableFuture`-shaped.
  - `infrastructure`: adapters — REST controllers, MongoDB projection (`SkyProjection`), MapStruct mappers, Keycloak OIDC, Liquibase + custom `ByteaEnforcedPostgresSQLDialect`, Axon configuration.
  - `app`: Spring Boot entry point and composition root.
- **Event sourcing**: Axon Framework 5.1.0 (`axon-framework-bom`, Spring Boot starter under groupId `org.axonframework.extensions.spring`). PostgreSQL holds the event store (`aggregate_event_entry` + `token_entry` + sequence `"aggregate-event-global-index-sequence"`); MongoDB holds query-side projections. Pooled (not tracking) event processor.
- **Schema**: Liquibase is the only path to schema change (`spring.jpa.hibernate.ddl-auto: validate` everywhere). Master changelog at `infrastructure/src/main/resources/db/changelog/db.changelog-master.yaml`; baseline `0001-axon-event-store-baseline` (Axon 4 baseline, frozen) followed by `0002-axon-5-event-store-migration` (drops Axon 4 tables, creates Axon 5's). Per ADR-0009, baselines are never edited — additions stack as new numbered changesets.
- **Security**: Keycloak realm reachable at `https://keycloak:9443/` (Docker `--add-host keycloak.test`). Bearer JWT on protected endpoints; production `SecurityConfig` wires the realm-roles JWT converter; method security via `@PreAuthorize`.
- **Runtime**: Spring profile `local` for direct run (`./gradlew bootRun --args='--spring.profiles.active=local'`); Dockerfile (`eclipse-temurin:25-jdk` build, `:25-jre-alpine` runtime) with custom Keycloak cert handling. Actuator probes exposed (`/actuator/health/{liveness,readiness}`).
- **Constraints**: Java 25 toolchain enforced; UTF-8 mandated; Liquibase is the only schema-change path; do not introduce dependencies that cross the inward-only module direction; do not put `@CommandHandler` methods on the aggregate (Axon 5 — they live in a separate `@Component` and receive `@InjectEntity`).