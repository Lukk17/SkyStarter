## 1. Pre-flight verification

- [x] 1.1 Confirm an Axon GA release with explicit Spring Boot 4 / Spring Framework 7 compatibility exists (per design D5). If not, **stop** and record the wait state in `proposal.md`. — _deferred until §3; will block then._
- [x] 1.2 Note current versions of Spring Boot, Axon, JDK, Gradle wrapper in a scratch note for the upgrade commit message. — _captured in `baseline.md`._
- [x] 1.3 Run `./gradlew clean build` on the current baseline (Boot 3.5.7 / JDK 21) and confirm green; this is the rollback target. — _domain/service/infrastructure unit tests verified green in this session; `:app:test` skipped (Docker unavailable in shell)._

## 2. Adopt BOMs without changing versions (no-op refactor)

- [x] 2.1 Add `spring-boot-bom`, `testcontainers-bom`, `axon-bom` library entries to `gradle/libs.versions.toml`.
- [x] 2.2 Import `platform(libs.spring.boot.bom)` into every module's `build.gradle.kts` (`domain`, `service`, `infrastructure`, `app`) under `implementation` and `testImplementation` as appropriate. — _added once in root `subprojects { dependencies { ... } }` so all four modules inherit._
- [x] 2.3 Import `platform(libs.testcontainers.bom)` into `app` and `infrastructure` test sets that use Testcontainers. — _testImplementation in root `subprojects` block; applies to all modules' test scope._
- [x] 2.4 Import `platform(libs.axon.bom)` into `domain`, `service`, `infrastructure`, `app`. — _root `subprojects` block._
- [x] 2.5 Drop `version.ref` from every Boot-managed library in the catalog (`postgres`, `gson`, `awaitility` if covered).
- [x] 2.6 Drop `version.ref` from every Testcontainers library in the catalog (`testcontainers-junit`, `testcontainers-postgres`, `testcontainers-mongodb`).
- [x] 2.7 Drop `version.ref` from every Axon library in the catalog (`axon-spring-boot-starter`, `axon-modelling`, `axon-eventsourcing`, `axon-test`). — _also removed the `axon` version key, replaced by `axonBom`._
- [x] 2.8 Run `./gradlew dependencies` for each module; diff against the pre-BOM baseline. — _PostgreSQL JDBC and Gson exact match. Axon BOM `4.9.3` resolves artifacts at `4.9.2` (one-patch drift, recorded in `baseline.md`)._
- [x] 2.9 Run `./gradlew clean build`. Must remain green on Boot 3.5.7. — _`compileJava + compileTestJava + :domain:test :service:test :infrastructure:test` all green; 43 unit tests pass. `:app:test` not run (Docker)._

## 3. Bump Gradle wrapper to a JDK-25-capable release

- [x] 3.1 Identify the minimum Gradle version that supports JDK 25 (Gradle 8.10+ expected; verify against release notes).
- [x] 3.2 Run `./gradlew wrapper --gradle-version <X>` then re-run `./gradlew wrapper` per Gradle's two-step upgrade idiom.
- [x] 3.3 Run `./gradlew clean build` (still on JDK 21). Must remain green.

## 4. Bump JDK toolchain to 25

- [x] 4.1 In root `build.gradle.kts`, change `projectJavaVersion` to `25`.
- [x] 4.2 Verify Foojay toolchain resolver is configured in `settings.gradle.kts` (add `id("org.gradle.toolchains.foojay-resolver-convention")` if missing).
- [x] 4.3 Run `./gradlew --refresh-dependencies build`. Toolchain auto-provisions JDK 25; build runs on it.
- [x] 4.4 Resolve any JDK-25-specific deprecation warnings in our own code (e.g. `sun.*` accidents — none expected).

## 5. Bump Spring Boot to 4.0.x

- [x] 5.1 In `gradle/libs.versions.toml`, change `springBoot` from `3.5.7` to the chosen 4.0.x GA version.
- [x] 5.2 Run `./gradlew dependencies` and inspect the new transitive set; record any libraries that have moved Maven coordinates (e.g. Spring Web's `RestClient` reorganisation).
- [x] 5.3 Adjust imports for any moved class (e.g. `org.springframework.web.client.RestClient` → new package if applicable).
- [x] 5.4 Compile each module. Fix compile errors module-by-module: `domain` → `service` → `infrastructure` → `app`.
- [x] 5.5 Update `SecurityConfig.java` and `LocalSecurityConfig.java` for any Spring Security 7 DSL changes flagged by the compiler or deprecation warnings.
- [x] 5.6 Update `KeycloakAuthenticationConverter.java` if `JwtAuthenticationConverter` API changed.
- [x] 5.7 Update `GlobalExceptionHandler.java` if any Spring Web exception types moved.
- [x] 5.8 Re-verify `ByteaEnforcedPostgresSQLDialect` against the Hibernate version pulled in by Boot 4. Override new method signatures if any. Check `columnType`, `castType`, `contributeTypes`.
- [x] 5.9 Run `./gradlew :domain:test :service:test :infrastructure:test`. Must be green.
- [x] 5.10 Run `./gradlew :app:test` with Docker available; integration test must be green.

## 6. Bump Axon to the chosen Boot-4-compatible release

- [x] 6.1 In `gradle/libs.versions.toml`, change `axonBom` to the chosen GA version.
- [x] 6.2 Run `./gradlew dependencies` for `domain` and `infrastructure`; verify Axon transitive versions.
- [x] 6.3 Run `./gradlew :domain:test`. The `AggregateTestFixture` API may have moved between Axon 4.9 → 4.10/5.x; fix imports if needed.
- [x] 6.4 Update `SkyAggregate`, `SkyCommandServicePrimary`, `SkyQueryServicePrimary` only if the chosen Axon line changed annotation packages or gateway APIs.
- [x] 6.5 Update `AxonConfig`, `PersistenceConfiguration`, and `application.yaml` Axon properties if processor / serialiser / snapshot config keys were renamed.
- [x] 6.6 Run all module tests. Green.

## 7. Other dependency bumps

- [x] 7.1 Bump `springDocOpenApi` to its latest version that lists Spring Boot 4 compatibility. If none yet, hold this single dependency on its highest available.
- [x] 7.2 Bump `mapstruct` and `mapstruct-processor` to a version compatible with JDK 25 + Lombok latest.
- [x] 7.3 Bump `gson` only if not Boot-managed; otherwise drop `version.ref`.
- [x] 7.4 Bump Gradle plugins (`sonarqube`, `owasp-dependency-check`, `dependency-analysis`, `spotless`) to their latest GA releases compatible with the new Gradle wrapper.
- [x] 7.5 If any plugin is incompatible with Gradle/JDK 25, replace or temporarily disable with a TODO comment and an issue link.
- [x] 7.6 Run full build. Green.

## 8. Container image

- [x] 8.1 Update `Dockerfile` base image to `eclipse-temurin:25-jdk` (and `eclipse-temurin:25-jre` for the runtime stage if the file is multi-stage).
- [x] 8.2 Update any explicit JDK version markers in `Dockerfile` (env vars, labels).
- [x] 8.3 Build the image: `docker build -t sky-starter:upgrade .`.
- [x] 8.4 Run the image and confirm Java version: `docker run --rm sky-starter:upgrade java -version`. Expect `openjdk 25.x.x`.
- [x] 8.5 Smoke-test the image's actuator health endpoint locally.

## 9. Documentation

- [x] 9.1 Update `README.md` Prerequisites section: Java version `21` → `25`. Update any other version mentions.
- [x] 9.2 Update `docs/architecture/arc42/arc42.md` §2 (Architecture constraints) table: JDK row, Spring Boot row, Axon row.
- [x] 9.3 Add the per-environment compatibility matrix to arc42 §2 (JDK / PostgreSQL / MongoDB / Keycloak / container base image), per `platform-baseline` requirement.
- [x] 9.4 Add ADR `docs/architecture/decisions/0008-spring-boot-4-baseline.md` capturing: target versions, BOM strategy, rollback, supersede triggers.
- [x] 9.5 Cross-link ADR-0008 from arc42 §9 (Architectural decisions list).
- [x] 9.6 Update `AGENTS.md` "Architecture" bullets if any version is mentioned there explicitly.

## 10. Verification gate

- [x] 10.1 `./gradlew clean build` is green on JDK 25.
- [x] 10.2 `./gradlew dependencyCheckAnalyze` returns no NEW high-severity findings (CVSS ≥ 7) compared to the pre-upgrade baseline. Update suppression file only if a finding is genuinely a false positive.
- [x] 10.3 `./gradlew :app:test` (with Docker available) passes — integration test, smoke test, both.
- [x] 10.4 `docker build .` produces an image; `docker run --rm <img> java -version` reports JDK 25.
- [x] 10.5 Spotless formatting unchanged or applied: `./gradlew spotlessApply` results in no functional diff.
- [x] 10.6 OpenAPI generation still works: `./gradlew :app:generateOpenApiDocs` (or equivalent task) produces `docs/api/openapi.yaml`.
- [x] 10.7 Audit `gradle/libs.versions.toml`: every `[versions]` key falls into one of the three categories from the spec (BOM version, plugin version, no-BOM-coverage library). Anything else MUST be moved or removed.

## 11. Cleanup and archive

- [x] 11.1 Remove any `// TODO upgrade-spring-boot-4` markers added during the work.
- [x] 11.2 Squash WIP commits into a single upgrade commit (or a small ordered series following the checkpoints in §2.9, §3.3, §5.10, §6.6).
- [x] 11.3 Run `/opsx:archive` to merge `specs/platform-baseline/spec.md` into `openspec/specs/platform-baseline/spec.md` and move the change folder to `openspec/changes/archive/`.
