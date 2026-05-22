# Development notes

Operational scratch-pad for working in this repo. Things that are easy to forget
between sessions, decisions that look like bugs but aren't, and the rationale
behind non-standard choices.

For architecture, see `AGENTS.md` (the inward dependency rule, hexagonal
layout, Axon 5 Entity Model) and the ADRs under `docs/architecture/` once
those land.

## Build cheatsheet

```bash
./gradlew :app:bootRun --args='--spring.profiles.active=local'
```

```bash
./gradlew test
```

```bash
./gradlew integrationTest
```

```bash
./gradlew check
```

`test` runs unit tests in parallel JVM forks (`availableProcessors / 2`).
`integrationTest` runs the contents of `**/integration/**` sequentially with
`forkEvery = 1` (fresh JVM per IT class -- prevents Axon lifecycle bean
state leaking between contexts), backed by shared Testcontainers. `check`
runs both plus the JaCoCo 0.80 INSTRUCTION gate. OWASP `dependencyCheckAggregate`
is a separate manual / CI step (see below) -- it can't coexist with
`org.gradle.parallel=true` in the same build invocation.

Cold `./gradlew check` is about 30 seconds.

## OWASP dependency-check

The OWASP plugin is applied at the root project only. Running the
aggregate task covers every subproject in one pass:

```bash
./gradlew dependencyCheckAggregate
```

No flags required. `gradle.properties` keeps `org.gradle.parallel=false`
because the plugin resolves subproject configurations at execution
time, which Gradle 9 rejects under parallel project execution
("Resolution of the configuration was attempted without an exclusive
lock"). If you want to opt into parallel for a specific run, pass
`--parallel` on the CLI -- but not when running this task.

The aggregate task (and `dependencyCheckAnalyze`) is marked
`notCompatibleWithConfigurationCache(...)`. The build still works, but
each invocation reports 10-15 "configuration cache problems" in the
log -- those are informational under `problems=warn`, not failures.
BUILD SUCCESSFUL is the truth; the cache just isn't reused for those
specific tasks.

The task is **not** wired into `check` -- treat it as a CI-side gate
or a release-time check.

Suppressions live in [`dependency-check-suppressions.xml`](../dependency-check-suppressions.xml).
Every entry has an `until` sunset date -- when that date passes,
`dependencyCheckAggregate` will re-fail the build on those CVEs and
the team must decide: upgrade, accept again with new rationale, or
replace the dep. Don't bump sunsets without a written note in the
Accepted findings table below.

## buildHealth (dependency-analysis)

```bash
./gradlew buildHealth
```

Reports unused dependencies, misclassified scopes, etc. Non-fatal; build
passes regardless. Treat findings as advice, not gospel -- the plugin
generates a lot of false positives against Spring Boot starters (it can't
see through autoconfiguration) and against bundle imports (it sees the
bundle members individually, not as a logical unit). Review per case
before changing build files.

## OpenAPI spec generation

`docs/api/openapi.yaml` is regenerated on every `./gradlew build`. The
springdoc plugin's task forks a Spring Boot process under the `local`
profile, hits `/openapi/v3/api-docs.yaml`, writes the spec to disk,
and shuts the process down -- no manual invocation needed.

```bash
./gradlew :app:generateOpenApiDocs    # standalone run, same effect
```

Prerequisite: PostgreSQL + MongoDB reachable when `build` runs (per
[`docs/running.md`](running.md) -- the `local` profile points at
`localhost:5432` and `localhost:27017`). Without them the forked
process fails to start and the build fails. CI containers running
the build need to provision those services first.

Commit the regenerated `openapi.yaml` whenever the public API
changes -- the file is the source of truth downstream consumers
read for client generation.

Testcontainers reuse is on by default for `integrationTest`. Gradle's
`environment("TESTCONTAINERS_REUSE_ENABLE", "true")` call sets that variable
**only** on the forked test JVM process -- it never touches your shell, your
OS, or `~/.testcontainers.properties`. When the test JVM exits, the variable
is gone. The container beans call `.withReuse(true)`, so Postgres / Mongo
persist across runs and the second IT loop is seconds, not ~30.

The three things people mix up here:

- `TESTCONTAINERS_REUSE_ENABLE` **environment variable** -- the route we use.
  Scope: the single process Gradle sets it on. Honored by Testcontainers.
- `~/.testcontainers.properties` **user-home file** -- machine-wide,
  per-developer. Honored by Testcontainers. Use this only if you want to
  override the default for every project on your machine.
- `-Dtestcontainers.reuse.enable=true` **JVM `-D` flag** -- looks like the
  natural Gradle-side choice. **Testcontainers does not read it** for the
  reuse flag. See https://java.testcontainers.org/features/reuse.

## Accepted findings — do not "fix"

The following look like defects in a casual review but are deliberate. Each
row says *what* the unusual choice is, *why* it was made, and *what to do
instead* if you're tempted to "clean it up". Update this table when you
encounter a finding the team has explicitly decided not to act on.

| Decision / observation | Why | If you're tempted to change it |
| --- | --- | --- |
| `SkyCommandHandlers` in `domain` is `@Component` | Axon 5 Entity Model requires external command handlers to be Spring beans so the Axon-Spring bridge can wire them with `@InjectEntity` + `EventAppender`. Co-located in `domain` to keep the bounded context coherent. | Don't move it out of `domain` -- the ArchUnit rule allows `@Component` in domain for this reason. Other Spring stereotypes (`@Service`, `@Configuration`, `@Repository`) remain prohibited. |
| `KeycloakAuthenticationConverter` maps realm roles **verbatim**, no `ROLE_` prefix | The `local` Keycloak realm already names its realm roles with the `ROLE_` prefix (`ROLE_USER`, `ROLE_ADMIN`). Adding another in the converter produces `ROLE_ROLE_USER` and 403s every authenticated request. | If `hasRole('USER')` stops resolving, check the realm seed, not the converter. The contract is locked by `JwtRolePrefixIT`-style tests if you add them. |
| `StartupLogConfig`'s JWK probe uses a permissive `TrustManager` | The probe is a **diagnostic** connectivity check, not a security control. It runs once at startup against the configured JWK Set URI and reports `[OK]` / `[FAILED]` to the log. A self-signed Keycloak in local dev otherwise produces `PKIX path building failed` noise. | Don't reuse the permissive `SSLContext` anywhere else. Real JWT validation uses Spring Security's `JwtDecoder`, which has the real trust store. |
| `version = "1"` in `@GetMapping` / `@PostMapping` matches URL `/v1/...` | Spring Framework 7's default `SemanticApiVersionParser` strips leading non-digits before parsing, so `"v1"` (from the URL segment) and `"1"` (from the annotation) both parse to `(1.0.0)` and match. | Don't add a custom `ApiVersionParser` to do the prefix stripping -- it's already free. |
| Unit `test` task **excludes** `**/integration/**`; `integrationTest` runs them | `@SpringBootTest` ITs share Testcontainers; running them across parallel forks would race on the same Postgres / Mongo data. Sequential `integrationTest` with `maxParallelForks = 1` sidesteps that. | Don't add an IT to the default `test` task. Put it in the `integration` package and let `integrationTest` pick it up. |
| `integrationTest` uses `forkEvery = 1` (fresh JVM per IT class) | When two `@SpringBootTest` ITs run in one JVM the second context boot fails with `Failed to start bean 'axon-start-lifecycle-handler-N'` because Axon's lifecycle bean doesn't tolerate being restarted on a polluted Spring context. Spawning a fresh JVM per IT class costs ~5-10s of Spring boot per class but Testcontainers reuse means the containers carry over -- so the actual wall-clock penalty is small. | If you increase the IT count by 10x, consider `@DirtiesContext(classMode = AFTER_CLASS)` and a higher `forkEvery` instead, but profile both first. |
| `dependency-check-suppressions.xml` carries 17 CVEs with `until="2026-08-22Z"` | Spring Boot 4.0.6 + Axon 5.1.0 transitive deps tripped `failBuildOnCVSS = 7.0` immediately. The sunset gives a 3-month window to upgrade, pin, or replace -- not "ignore forever". | When sunset hits, **don't bump the date** without addressing the root cause. Each unaddressed entry deserves a paragraph in this table explaining why it's accepted longer. |
| Spring Modulith BOM is **not** imported | Module boundaries here are Gradle subprojects -- compile-time, JAR-level. Modulith's package-level test-time enforcement would be strictly weaker. | Don't re-add the BOM. If you need module-style features within one subproject, propose flattening to a single module first. |
| ArchUnit pinned to **1.4.2**, not 1.4.0 | 1.4.0's bundled ASM doesn't recognise Java 25 bytecode -- it silently parses zero classes from the import path and every rule fails with "failed to check any classes". 1.4.2 has the updated ASM. | If you bump back to 1.4.0 (or some 1.5.x with the same regression), `HexagonalArchitectureTest` will silently green-light everything. Check `CLASSES.size()` before trusting a passing run. |
| `HexagonalArchitectureTest` uses **explicit `importPaths(Path...)`**, not `@AnalyzeClasses` | Gradle's test launcher uses a `Class-Path` manifest jar (to avoid Windows command-line length limits). The launched JVM has the launcher on `java.class.path` and the real entries inside its manifest -- ArchUnit's default classpath traversal doesn't find them. Explicit paths from `project.root.dir` system property are deterministic. | Don't switch to `@AnalyzeClasses(packages = ...)`. It'll find zero classes and produce green tests that prove nothing. |
| JaCoCo coverage gate excludes `*Config` / `*Configuration` / `*MapperImpl` / `SkyUser` / `ByteaEnforcedPostgresSQLDialect` / `ApiCommon*Responses` / `ProblemTypes` / `AppApplication` | These are bean-wiring, Hibernate plumbing, generated code, annotation interfaces, and constants -- they carry no test-meaningful logic. They're either exercised by Spring context bootstrap or have nothing to assert. Including them would force noise-tests for shape's sake. | If you write logic inside one of these (a non-trivial `@Configuration` method, real branches in a dialect), narrow the exclusion or test it directly. Don't relax the 0.80 floor to compensate. |
| Command-handler audit-log subject is passed as an **explicit log argument**, not pulled from MDC at completion time | `CompletableFuture.whenComplete` may run on a different thread; the MDC populated by `JwtSubjectMdcFilter` is request-thread-local and not propagated. Capturing the subject on the calling thread and passing it as a log arg keeps it correct on async completion. | Don't rely on `%X{jwt.subject}` in the log pattern for async completion logs -- it'll render empty. The MDC key is correct for the request thread only. |
| Bruno `port` lives on the **collection** (`opencollection.yml`), not the env | Port and API base path are project-specific -- different services on the same host use different ports. The env layer only carries values that change per-environment (protocol, host, Keycloak coordinates, tokens). | Don't move `port` back to `environments/*.yml`. New services should set their own collection-level `port`. |
| `apiPath` is **inlined** as `/v1/starter` in every Bruno request URL | One API, one path. Variable-extracting it adds indirection without enabling any deployment. | If a future version (`/v2/skies`) lands, update the literals in the collection -- still cleaner than juggling a path variable. |
| Spring Boot 4 native API versioning declares `{version}` as a URI variable in `@RequestMapping`, ignored by methods | Spring requires the version segment to be present as a URI variable in the controller mapping (`/{version}/starter`), even though the version routing strategy extracts segment 0 independently. Methods don't bind `{version}` -- they only bind `{skyId}`. | Don't try to remove `{version}` from `@RequestMapping`. Spring's `RequestMappingInfo` builder enforces it. |
| `gradle.properties` sets `org.gradle.configuration-cache.problems=warn` (not `fail`) | Some plugins (notably OWASP `dependency-check`, `dependency-analysis`) emit configuration-cache problems we can't fix upstream. Warning keeps the build moving while still surfacing the issues. | If a *first-party* configuration-cache problem appears in our own build script, fix it. Don't promote everything to `fail` without auditing the plugin tree first. |
| `integrationTest` enables Testcontainers reuse via Gradle's `environment(...)` call, not `systemProperty(...)` | Testcontainers reads the reuse flag from exactly two places: the `TESTCONTAINERS_REUSE_ENABLE` env var (process-scoped) or the user-home file `~/.testcontainers.properties` (machine-wide). The `-Dtestcontainers.reuse.enable=true` JVM flag is **not** honored even though it looks like it should be -- the call silently does nothing. Docs: https://java.testcontainers.org/features/reuse. | If reuse stops kicking in, check that `TESTCONTAINERS_REUSE_ENABLE=true` made it through Gradle's `environment(...)` into the forked test JVM. Don't "fix" it back to `systemProperty(...)`. |

## OpenSpec lifecycle

This project uses [OpenSpec](https://github.com/Fission-AI/OpenSpec) for
spec-driven development. The full lifecycle (commands run inside your
agent shell) is documented in `AGENTS.md` -- propose, apply, verify,
archive.

When a finding here gets contradicted by an OpenSpec change, update the
table in the **same** change as the code edit so the rationale doesn't
drift.
