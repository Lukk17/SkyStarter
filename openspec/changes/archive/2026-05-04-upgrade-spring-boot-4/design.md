## Context

SkyStarter is currently on Spring Boot **3.5.7** with a Java **21** toolchain. The version catalog (`gradle/libs.versions.toml`) carries explicit version pins for Spring Boot, springdoc-openapi, postgres, mapstruct, gson, axon, plus the Gradle plugins. Most of those pins are redundant with the Spring Boot dependency-management plugin already in use — they exist mainly because the catalog was written without exploiting BOMs.

The upgrade target is Spring Boot **4.0.x** (Spring Framework 7, Spring Security 7), which mandates JDK **25**. Axon Framework will go to whatever GA line is compatible with Boot 4 at apply time (today: Axon 4.10.x is on the Boot-4 compatibility track; Axon 5 is in milestone). The change also takes the opportunity to adopt three additional BOMs: **Spring Boot BOM** (already in use, but underused), **Testcontainers BOM**, and **Axon BOM**.

Constraints:
- Hexagonal module layout MUST remain intact (ADR-0001).
- Public API surface (`/v1/starter/**`) MUST NOT change.
- Event store schema MUST NOT change (no Liquibase / Flyway introduction in this change — ADR-0007 still applies).
- Tests must remain green; CI must remain green.

Stakeholders: maintainer (Lukk17), any team forking the template.

## Goals / Non-Goals

**Goals:**
- Land Spring Boot 4 + Spring Framework 7 + Spring Security 7 across all four modules.
- Move every BOM-coverable version from explicit pins to BOM resolution.
- Establish `platform-baseline` as the durable spec for "what versions does this template stand on".
- Bump the Docker base image and the README prerequisites to match.
- Add ADR-0008 capturing the upgrade decision and BOM strategy so the next upgrade has a precedent.

**Non-Goals:**
- No API or schema changes.
- No new functional features.
- No swap of any datastore or IdP.
- Not introducing migration tooling (Liquibase / Flyway) — out of scope, still tracked in ADR-0007.
- Not migrating from Lombok to records or any other refactor disguised as an upgrade.
- Not switching from Axon 4.x to Axon 5.x unless 5.x is GA AND Boot-4-compatible AND drop-in for our usage.

## Decisions

### D1. Adopt three platform BOMs

We import three platform BOMs in every Gradle module that consumes their libraries:

```kotlin
dependencies {
    implementation(platform(libs.spring.boot.bom))      // version managed by spring-dependency-management already
    implementation(platform(libs.testcontainers.bom))   // NEW
    implementation(platform(libs.axon.bom))             // NEW
    // …
}
```

The Spring Boot BOM is already implicitly in play via the `spring-dependency-management` plugin; we keep it for clarity and to support the future case where we want to use the BOM without the plugin (e.g. in non-Spring modules).

**Why these three, not more?** They cover ~80% of our pinned versions. springdoc-openapi has no BOM. Gson is Spring-Boot-managed. MapStruct does not publish a BOM (single artefact pair).

**Alternatives considered:** Spring Cloud BOM (we're not on Spring Cloud), Jackson BOM (already pulled in transitively by Spring Boot BOM).

### D2. Catalog restructure

`gradle/libs.versions.toml`:
- `[versions]` shrinks to: `springBoot`, `axonBom`, `testcontainersBom`, plus pure-pin libraries (springdoc-openapi, mapstruct, gson, the OWASP / Sonar / Spotless / Dependency Analysis Gradle plugins) and toolchain version.
- All Spring Boot–managed libraries lose their `version.ref` (e.g. `postgres`).
- Axon entries lose their `version.ref` (replaced by the Axon BOM).
- Testcontainers entries lose their `version.ref` (replaced by the Testcontainers BOM).
- New BOM entries:
  ```toml
  spring-boot-bom        = { module = "org.springframework.boot:spring-boot-dependencies", version.ref = "springBoot" }
  testcontainers-bom     = { module = "org.testcontainers:testcontainers-bom", version.ref = "testcontainersBom" }
  axon-bom               = { module = "org.axonframework:axon-bom", version.ref = "axonBom" }
  ```

**Alternatives considered:** keep version pins everywhere (rejected — that's the status quo we're fixing). Flatten the catalog into a single `[plugins]+[libraries]` block (rejected — version-keys-as-config is a Gradle idiom, keep it).

### D3. JDK and Gradle wrapper

- JDK **25** (Adoptium Temurin). `projectJavaVersion = 25` and `JvmVendorSpec.ADOPTIUM` in `build.gradle.kts`.
- Gradle wrapper bumped to a release that supports JDK 25 (≥ 8.10 expected). `./gradlew wrapper --gradle-version <X>` is the mechanism, run as part of the implementation.
- Dockerfile base image: `eclipse-temurin:25-jdk` (or `-jre` for the runtime stage if multi-stage is in use).

**Alternatives considered:** JDK 21 LTS (rejected — Spring Boot 4 mandates 25). JDK 24 (rejected — non-LTS, drops out of long support before adopters move).

### D4. Spring Security 7 DSL adjustments

Spring Security 7 finishes the deprecation of the non-lambda DSL and removes a number of `Customizer.withDefaults()` shortcuts. Our security configs already use the lambda DSL; the work is:
- Verify `requestMatchers` is still on `HttpSecurity#authorizeHttpRequests` (it is — no change).
- Replace any `AbstractHttpConfigurer::disable` references the new APIs deprecate; if Security 7 keeps them, no change.
- Re-test the `KeycloakAuthenticationConverter` against Security 7's `JwtAuthenticationConverter` (constructor / setter signatures unchanged across 6 → 7 per current snapshots).
- `LocalSecurityConfig` offline-decoder: `Jwt.Builder` API stable across 6 → 7.

**Alternatives considered:** rewrite to Spring Security's new "AuthorizationManager" composition style (rejected — out of scope for an upgrade).

### D5. Axon version selection

At apply time:
1. Check `axon-bom` releases on Maven Central.
2. Pick the highest GA whose release notes explicitly list Spring Boot 4 / Spring Framework 7 compatibility.
3. If no such release exists, **stop the upgrade** and document the wait state in the change folder. We do not back-port.

If Axon 5.x is GA AND Boot-4-compatible AND its API changes are mechanical for our usage (`@Aggregate`, `@CommandHandler`, `@EventSourcingHandler`, `CommandGateway.send`, `QueryGateway.query`), prefer 5.x. Otherwise stay on the latest 4.x.

**Alternatives considered:** Pin to a snapshot or milestone (rejected — templates ship GA only).

### D6. Hibernate dialect & PostgreSQL

`ByteaEnforcedPostgresSQLDialect` extends `PostgreSQLDialect` from Hibernate. Spring Boot 4 ships Hibernate **7.x** (project status as of late 2025). The dialect class hierarchy is largely stable, but two methods we override (`columnType`, `castType`, `contributeTypes`) need re-verification:
- If signatures match, no change.
- If signatures changed, override the new versions; failing test would be `AppApplicationTests.contextLoads()` against a Postgres Testcontainer.

PostgreSQL minor version target stays at 15 (already what `DatabaseVersion.make(15, 0)` declares).

### D7. Testcontainers and Awaitility

Testcontainers BOM also covers `org.awaitility:awaitility` indirectly via the Spring Boot BOM (Awaitility is Boot-managed). After upgrade, the `awaitility` catalog entry MUST drop its `version.ref` if the Boot BOM manages it; if it does not, keep the explicit pin and add a comment.

### D8. ADR + arc42 update

- New `docs/architecture/decisions/0008-spring-boot-4-baseline.md` capturing this decision.
- Update `docs/architecture/arc42/arc42.md` §2 (Architecture constraints): JDK row 21 → 25, Spring Boot row 3.5.x → 4.0.x, Axon row updated.
- `README.md` Prerequisites section: Java version updated.

### D9. Verification gate

Before declaring the change `done`:
1. `./gradlew clean build` is green on JDK 25.
2. `./gradlew dependencyCheckAnalyze` returns no high-severity new findings (CVSS ≥ 7) compared to the pre-upgrade baseline.
3. `./gradlew :app:test` (with Docker available) passes the integration tests.
4. `docker build .` produces an image; `docker run --rm <img> java -version` reports 25.
5. The compatibility-matrix table in arc42 §2 is updated.

## Risks / Trade-offs

- **Axon Boot-4 compatibility lag** → Mitigation: per D5, the change pauses if no compatible Axon GA exists. Better than back-porting.
- **Hibernate 7 dialect API churn** → Mitigation: integration test catches it on first context load. If override signatures changed, the fix is mechanical.
- **JDK 25 not yet on every developer's machine** → Mitigation: Gradle toolchain auto-provisioning via Foojay handles it; documented in README.
- **Spring Security 7 removed `Customizer.withDefaults()` overloads we use** → Mitigation: `SecurityConfig` already uses explicit lambdas after the prior bug-fix; we only need to verify `LocalSecurityConfig` similarly.
- **Awaitility moved out of the Boot BOM** → Mitigation: the catalog edit is small; the verification step catches a missing version key as a build failure.
- **Plugin compatibility with Gradle 8.10+** → Mitigation: bump plugins to their latest GA in the same change; D9 step 1 catches incompatibilities.
- **OWASP Dependency Check baseline noise** → Mitigation: regenerate suppression file as part of the change; do not silence high-severity findings, escalate to follow-up changes.
- **Forks pinned to Boot 3 may have to skip this upgrade** → Mitigation: this is the entire point of the template; we accept the breakage and document it in the README.

## Migration Plan

This is a template-only repo, so "migration" means "what does the upgrade commit look like":

1. Adopt the three BOMs in `gradle/libs.versions.toml` and every module's `build.gradle.kts` — should be a no-op runtime change while still on Boot 3.5.7. Verify build is green at this checkpoint.
2. Bump Spring Boot version in the catalog. Re-run build; fix any deprecation breaks (Spring Security DSL, Spring Web RestClient API, etc.).
3. Bump JDK toolchain to 25. Re-run build. Bump Gradle wrapper if needed.
4. Bump Axon version (per D5). Re-run domain + service tests.
5. Update Dockerfile base image. Build and smoke-test the container.
6. Update arc42 §2 and README; add ADR-0008.
7. Run full verification gate (D9).

There is no rollback strategy beyond `git revert` of the upgrade commit; the template has no production deployment of its own.

## Open Questions

- Is Axon 5.x GA at apply time? Decides D5.
- Does Spring Boot 4 BOM manage `awaitility`? Decides D7's `version.ref` drop.
- Does the OWASP Dependency Check plugin's latest version still support Gradle's configuration cache (which we may want to enable later)? Out of scope for this change but worth noting.
- Do we want to enable Gradle configuration cache as part of this change, or in a follow-up? **Recommendation: follow-up.** Keep this change about versions only.
