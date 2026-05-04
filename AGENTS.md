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

SkyStarter is a Spring Boot 3 backend service (Java 21, Gradle Kotlin DSL multi-module) demonstrating an event-sourced "Sky" domain. It exposes REST endpoints, persists events via Axon Framework, and integrates with Keycloak for OIDC-based authentication. The repo is a reference / starter template for CQRS + Event Sourcing on Spring Boot — used by the maintainer (Lukk17) to bootstrap new services and exercise hexagonal architecture patterns. Group `com.revdevs.pharmacy`, root project `sky-starter`.

## Architecture

- **Stack**: Java 21 (Adoptium), Spring Boot (version pinned in `gradle/libs.versions.toml`), Gradle Kotlin DSL with version catalog, Lombok, JUnit Platform.
- **Build**: Multi-module Gradle build — `domain`, `service`, `infrastructure`, `app` (see `settings.gradle.kts`). Shared config in `build-logic/`. Static analysis via Spotless (auto tasks disabled), SonarQube, OWASP Dependency Check (fail on CVSS ≥ 7), and Gradle Dependency Analysis. JaCoCo applied to all subprojects.
- **Patterns**: Hexagonal (Ports & Adapters) + CQRS + Event Sourcing + Event-Driven. Strict inward dependency rule: `app → infrastructure → service → domain`; `domain` depends on nothing.
  - `domain`: aggregates (e.g. `SkyAggregate`), domain events (e.g. `SkyCreatedEvent`), commands/queries, rich domain models. No framework leakage.
  - `service`: use-case orchestration over the domain.
  - `infrastructure`: adapters — Axon Framework wiring, MongoDB event store / projections, PostgreSQL via JPA/Hibernate, REST controllers, Keycloak OIDC.
  - `app`: Spring Boot entry point and composition root.
- **Event sourcing**: Axon Framework with `@ProcessingGroup` projections. MongoDB stores events; PostgreSQL holds query-side projections (Hibernate dialect customized; Liquibase has been removed — schema is managed via JPA/Hibernate).
- **Security**: Keycloak realm reachable at `https://keycloak:9443/` (Docker `--add-host keycloak.test`). Bearer JWT on protected endpoints.
- **Runtime**: Spring profile `local` for direct run (`./gradlew bootRun --args='--spring.profiles.active=local'`); Dockerfile included with custom certificate handling. Actuator probes exposed.
- **Constraints**: Java 21 toolchain enforced; UTF-8 encoding mandated; no Liquibase; `@ProcessingGroup` required on Axon projections; do not introduce dependencies that cross the inward-only module direction.