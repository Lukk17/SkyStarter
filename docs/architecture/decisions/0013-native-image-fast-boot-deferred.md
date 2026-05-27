# 0013 — GraalVM native image / fast boot is deferred

- **Status:** Accepted
- **Date:** 2026-05-27
- **Deciders:** Project maintainer
- **Tags:** graalvm, native-image, startup, axon, scope, deferred

## Context and problem statement

The question came up of compiling SkyStarter to a GraalVM native image for very
fast startup. Native image needs build-time **reachability metadata** for every
type touched by reflection, JNI, resources, or proxies. An event-sourced app is
reflection-heavy: Axon reflects over event / command / query message types,
dispatches handlers reflectively, and reconstitutes entities by replaying
events.

Inspecting the dependencies on the classpath:

- All ten **Axon 5.1.0** jars (including the Spring Boot starter and
  autoconfigure) ship **zero** GraalVM native-image metadata, and Axon is not
  in the GraalVM reachability-metadata repository.
- Spring Boot ships its own metadata, and Hibernate / PostgreSQL / MongoDB /
  Jackson are covered by the metadata repository the native build plugin pulls
  from — so those are not the problem. **Axon is.**

## Decision drivers

- Prefer a clean, low-maintenance result over a shiny but fragile one.
- A long-running service barely benefits from sub-second boot (this is not a
  CLI or a scale-to-zero function).
- The template must keep working as Axon 5 evolves; hand-written native hints
  against a brand-new API are a standing maintenance liability.

## Considered options

1. **Full GraalVM native now.** Hand-author and maintain all of Axon's
   reachability hints. Fragile, high-maintenance against a 5.x API still
   settling, for marginal benefit on a long-running service.
2. **Defer native; reach for Spring AOT + CDS if boot ever matters (chosen).**
   Class Data Sharing (AppCDS) and AOT are well supported on Java 25 / Spring
   Boot 4, give a real startup and warmup improvement, and need no
   Axon-native heroics.
3. **Do nothing and never revisit.** Loses the (small) future upside.

## Decision

We chose **Option 2**. SkyStarter ships as a normal JVM jar on
`eclipse-temurin:25-jre-alpine`. Native image is not pursued while Axon ships no
reachability metadata.

If startup or warmup ever becomes a real concern, the lever is **Spring AOT +
AppCDS**, not native — e.g. create a shared archive at build time and start
against it:

```shell
java -XX:ArchiveClassesAtExit=app.jsa -Dspring.context.exit=onRefresh -jar app.jar
```

```shell
java -XX:SharedArchiveFile=app.jsa -jar app.jar
```

Revisit native only once Axon publishes GraalVM reachability metadata (or joins
the metadata repository), at which point the cost/benefit flips.

## Consequences

### Positive

- No fragile, hand-maintained native hints tracking a moving Axon 5 API.
- A documented, low-risk fast-boot path (AOT + CDS) for when it's actually
  needed.

### Negative / accepted trade-offs

- No sub-second boot today — acceptable for a long-running event-sourced
  service.

## Links

- [ADR-0010](0010-upgrade-to-axon-5.md) — Axon 5 upgrade.
- [ADR-0012](0012-read-model-caching-deferred.md) — the sibling "deferred
  add-on" decision for read-model caching.
