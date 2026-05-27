# 0001 — Hexagonal module layout

- **Status:** Accepted
- **Date:** 2025-11-15
- **Deciders:** Project maintainer
- **Tags:** architecture, modules, hexagonal

## Context and problem statement

A starter template that downstream teams will fork and extend has to push back hard against the most common rot: business logic seeping into controllers, JPA entities masquerading as the domain, "service" classes that secretly know about Spring context. The default Spring Boot single-module layout makes this leakage easy and invisible.

We need a structure that makes the inward-only dependency rule **mechanically enforceable** — i.e. a violation breaks the build, not just a code review.

## Decision drivers

- Domain code must be free of Spring, JPA, MapStruct, and HTTP concerns.
- Test feedback loop on the domain must be sub-second (no Spring context).
- New contributors should be able to identify "where does X go?" by file path alone.
- Match the team's existing hexagonal vocabulary (ports, adapters, use cases).

## Considered options

1. **Single-module Spring Boot project** with package-by-feature.
2. **Two modules** — `domain` and `app` — with infrastructure and service folded into `app`.
3. **Four modules** — `domain`, `service`, `infrastructure`, `app` — with strict inward dependency direction.

## Decision

We chose **option 3**: four Gradle modules with the dependency rule `app → infrastructure → service → domain`.

- `domain` declares only the Axon API (commands/events/aggregate annotations) — no Spring, no Jakarta, no DB.
- `service` implements the domain ports using Axon gateways.
- `infrastructure` houses every adapter (REST, persistence, security, mappers, exception handler).
- `app` is the Spring Boot composition root and contains nothing but the `main` and Boot-specific assembly.

The dependency direction is encoded in each module's `build.gradle.kts`. Adding a reverse-direction dependency fails the build.

## Consequences

### Positive

- Domain tests run against plain JUnit + Axon test fixture — no Spring startup cost.
- Infrastructure can be replaced (e.g. swap Mongo for another store) without touching `domain` or `service`.
- Reviewers can spot architecture violations from the import statements of any class.

### Negative / accepted trade-offs

- More boilerplate for trivial code paths (DTO ↔ domain ↔ entity mapping).
- A new feature touches more files than it would in a single-module layout.
- MapStruct + Lombok annotation-processor ordering needs the `lombok-mapstruct-binding` shim.

## Links

- [arc42 §4](../arc42/arc42.md#4-solution-strategy)
- [diagram: module dependencies](../diagrams/03-module-dependencies.md)
