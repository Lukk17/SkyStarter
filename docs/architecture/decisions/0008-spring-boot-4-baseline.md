# 0008 — Spring Boot 4 baseline + BOM-first version management

- **Status:** Accepted
- **Date:** 2026-05-04
- **Deciders:** Project maintainer
- **Tags:** platform, upgrade, boms

## Context and problem statement

The template was on Spring Boot 3.5.7 / Java 21 with most library versions pinned explicitly in `gradle/libs.versions.toml`. Spring Boot 4.0 (GA late 2025) is the modern baseline: it requires Java 25, ships Spring Framework 7 / Spring Security 7 / Jackson 3, and reorganises several packages. Holding the template on 3.5 condemns every fork to the same upgrade chore later.

This change also tightens version management. Most pinned versions were redundant with what the Spring Boot dependency BOM already manages — they existed only because the catalog was originally written without exploiting BOMs.

## Decision drivers

- Templates should land on the latest GA so forks start there too.
- Fewer pinned versions = fewer drift points across forks.
- Library coordinates that move (Boot 4's persistence/webmvc-test split, Jackson 3's package rename) must be reflected once, not every time a fork upgrades.
- Keep the Axon Framework's compatibility risk explicit — Axon's release cadence trails Spring Boot.

## Considered options

1. **Stay on Spring Boot 3.5.x** until forks demand the upgrade.
2. **Upgrade to Spring Boot 4.0.x and adopt BOMs aggressively** (this change).
3. **Upgrade to Spring Boot 4.0.x without BOM rework** — minimal commit but leaves redundant pins.

## Decision

We chose **option 2**:

- **Spring Boot 4.0.6** + **Java 25** (via Foojay toolchain resolver).
- **Five platform BOMs** imported in every module via `platform(...)`:
  - `spring-boot-dependencies` (4.0.6)
  - `spring-cloud-dependencies` (2025.1.1)
  - `spring-modulith-bom` (2.0.6)
  - `testcontainers-bom` (2.0.5)
  - `axon-bom` (4.9.3 → artifacts 4.9.2)
- Every BOM-coverable library lost its `version.ref` in the catalog. The `[versions]` block now only contains: BOM versions, plugin versions, and pin-only libraries (springdoc-openapi-starter, MapStruct, Lombok-MapStruct binding, ArchUnit).
- ArchUnit (1.4.0) added as a `testImplementation` for future architecture tests (e.g. enforcing the hexagonal dependency rule). No tests written in this change.

### Code-level changes absorbed by the upgrade

| Area | Change |
|---|---|
| `@EntityScan` | Moved from `org.springframework.boot.autoconfigure.domain` → `org.springframework.boot.persistence.autoconfigure`. |
| `@AutoConfigureMockMvc` | Moved from `org.springframework.boot.test.autoconfigure.web.servlet` → `org.springframework.boot.webmvc.test.autoconfigure`; provided by the new `spring-boot-starter-webmvc-test` starter. |
| Jackson | `com.fasterxml.jackson.*` → `tools.jackson.*` (Jackson 3 namespace migration). |
| `NoResourceFoundException` | Constructor signature widened: `(HttpMethod, String, String)`. |
| Spring Security `JwtAuthenticationConverter` | Now adds a `FactorGrantedAuthority` automatically (MFA step-up). Tests must filter authorities by type when asserting the realm-role mapping. |
| Testcontainers 2.x | All modules renamed to `testcontainers-<name>` (e.g. `org.testcontainers:postgresql` → `org.testcontainers:testcontainers-postgresql`). |

## Consequences

### Positive

- Template forks start on the modern Boot 4 + Java 25 stack.
- Single-version drift events are cheaper: bumping a BOM updates dozens of libraries at once.
- Axon's Boot-4 compatibility risk is now an explicit BOM-version decision, not buried in scattered pins.
- ArchUnit is on the classpath; the next change can write `domain → service → infrastructure → app` enforcement tests with no further setup.

### Negative / accepted trade-offs

- The Axon BOM `4.9.3` resolves Axon artifacts at `4.9.2` — a one-patch lag we accept because the BOM hasn't republished.
- Two non-BOM pins remain in the catalog (`springdoc-openapi-starter`, MapStruct/Lombok-MapStruct binding, ArchUnit). They are documented inline so the next upgrade knows why.
- Java 25 requires Foojay or a manually installed JDK on every machine. Documented in the README.
- Spring Boot 4's package reorganisations (persistence/webmvc-test splits, Jackson 3 namespace) mean any fork carrying patches against the old packages must rebase. This is the cost we pay once, in the template, so forks don't pay it themselves.

## Links

- [arc42 §2 — Architecture constraints](../arc42/arc42.md#2-architecture-constraints) (compatibility matrix)
- [ADR-0001 — Hexagonal module layout](0001-hexagonal-module-layout.md)
- [ADR-0002 — CQRS with Axon](0002-cqrs-with-axon.md)
- [ADR-0006 — Tracking processor with segmentation](0006-tracking-processor-segmentation.md)
- OpenSpec change folder: `openspec/changes/upgrade-spring-boot-4/` (proposal, design, specs/platform-baseline, tasks).
