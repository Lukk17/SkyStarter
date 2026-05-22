# AGENTS.md

This file provides shared instructions to all AI coding agents working in this repository (Claude Code, Kilo Code, OpenCode, Codex CLI). Standards and skills are imported from [agent-standards](https://github.com/Lukk17/agent-standards).

## Skills

This project includes agent skills in `.agents/skills/`. Invoke relevant skills before starting implementation work. Examples:

- `/code-reviewer` before reviewing code
- `/security-review` before auditing for vulnerabilities
- `/coding-standards` before writing new code
- `/tdd-workflow` before adding features or fixing bugs

Slash commands may appear as `/name` or `/name.md` in your agent's autocomplete — use whichever your agent shows.

## Subagents

This project ships 19 specialised subagents — narrow-scope agents the main session delegates to. Claude Code reads
`.claude/agents/`; OpenCode and Kilo Code both read `.opencode/agents/`. Codex CLI has no per-agent file mechanism — it
sees `AGENTS.md` plus skills only.

These files are generated artifacts pulled from agent-standards. Do **not** hand-edit them — changes will be
overwritten on the next pull. To modify a subagent permanently, edit its canonical source in the agent-standards repo
(`subagents/<name>.md`), regenerate there, and re-import.

A few of the most-used:

- `code-reviewer` — security-aware diff review before merge
- `test-automator` — write missing tests and fix failures without weakening assertions
- `security-auditor` — threat modelling, secure-coding review, compliance gap analysis
- `backend-architect` — contract-first service and API design
- `database-expert` — schema design and query / index optimisation
- `debugger` — root-cause analysis for a single failing test or runtime error
- `devops-troubleshooter` — live incident response with postmortem

Full catalogue: see the agent-standards README's "Subagents catalog" section, or list `.claude/agents/*.md` (or
`.opencode/agents/*.md`) in this project.

## MCP servers

MCP servers are configured per-agent at the project root: `.mcp.json` for Claude Code and `opencode.json` for OpenCode
and Kilo Code. Both files currently declare two servers:

- **context7** — fetches current library / framework docs (Spring Boot, Axon, Hibernate, etc.). Prefer this over web
  search or training-data recall when answering version-sensitive API questions.
- **mongodb** — read-only `mongodb-mcp-server` against the local Mongo instance (`MONGODB_URL`, default
  `mongodb://localhost:27017`). Use it to inspect projection state when debugging the query side.

Check your tool list at startup and use these when they're a better fit than re-deriving the answer from local files.
Human-side setup lives in [`docs/MCP_SETUP.md`](docs/MCP_SETUP.md).

## Working With Agents

All supported agents read this `AGENTS.md` from the project root and auto-discover skills from `.agents/skills/`. Start your agent from the project root:

- **Claude Code** — run `claude`. Reads `.claude/CLAUDE.md`, which imports this file.
- **Kilo Code** — reads `AGENTS.md` automatically. Optional `kilo.jsonc` for extra config.
- **OpenCode** — reads `AGENTS.md` automatically. Optional `opencode.json` at project root.
- **Codex CLI** — run `codex`. Reads `AGENTS.md` automatically. Global settings in `~/.codex/config.toml`.

## Working Principles

Apply these to every task, in order. They govern *how* you work; the `coding-standards` skill governs *what the code
should look like*.

### 1. Think Before Coding

State assumptions explicitly. When the prompt is ambiguous, surface the interpretations and ask — do not pick one
silently and run with it. If a simpler approach exists, propose it before writing code. Stop and ask when genuinely
unsure — a clarifying question costs less than a wrong implementation.

### 2. Simplicity First

Write the minimum code that solves the problem.

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for scenarios that cannot happen.
- If 200 lines could be 50, rewrite it.

Test: would a senior engineer call this overcomplicated? If yes, simplify.

### 3. Surgical Changes

Touch only what the task requires.

- Do not "improve" adjacent code, comments, or formatting.
- Do not refactor code that is not broken.
- Match existing style, even if you would write it differently.
- If you notice unrelated dead code, mention it — do not delete it.
- Remove imports, variables, and helpers that *your* changes orphan. Leave pre-existing dead code alone unless asked.

Test: every changed line should trace directly to the request.

### 4. Goal-Driven Execution

Define success before starting. Convert vague asks into verifiable goals:

| Instead of...       | Transform to...                                                       |
| ------------------- | --------------------------------------------------------------------- |
| "Add validation"    | "Write tests for invalid inputs, then make them pass"                 |
| "Fix the bug"       | "Write a failing test that reproduces it, then make it pass"          |
| "Refactor X"        | "Ensure tests pass before and after, behavior unchanged"              |

For multi-step work, state the plan first:

1. `<step>` → verify: `<check>`
2. `<step>` → verify: `<check>`
3. `<step>` → verify: `<check>`

Then loop until each check passes. Do not claim a task is done without running the verification.

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
