# Code quality

## Code formatter

The project uses IntelliJ IDEA's default formatter. The exported scheme is checked in at [`DefaultFormatting.xml`](../DefaultFormatting.xml). Import it via *Settings → Editor → Code Style → Scheme → Import…*.

### Line separator

Use **Unix line endings (LF)** — Windows CRLF causes friction with Docker images and shell scripts.

In IntelliJ:

> Settings → Editor → Code Style → *Line separator* → **Unix and macOS (\n)**

In Git on Windows:

```shell
git config --global core.autocrlf input
```

On Linux / macOS:

```shell
git config --global core.autocrlf false
```

The repo also pins the EOL via [`.gitattributes`](../.gitattributes), which trumps the global config for this project.

## OWASP Dependency-Check

[OWASP Dependency-Check](https://plugins.gradle.org/plugin/org.owasp.dependencycheck) scans the dependency graph for known CVEs. The build fails on CVSS ≥ 7.0 (configured in `build.gradle.kts`).

```shell
./gradlew dependencyCheckAggregate
```

Report: [`build/reports/dependency-check-report.html`](../build/reports/dependency-check-report.html) (after the task runs).

For faster, unthrottled scans, request a free NVD API key at <https://nvd.nist.gov/developers/request-an-api-key> and add it to [`gradle.properties`](../gradle.properties):

```properties
nvdApiKey=<api-key>
```

## Dependency Analysis

[Dependency-Analysis-Gradle-Plugin](https://plugins.gradle.org/plugin/com.autonomousapps.dependency-analysis) inspects the actual usage of declared dependencies and reports unused or under-declared ones.

```shell
./gradlew buildHealth
```

Report: [`build/reports/dependency-analysis/build-health-report.txt`](../build/reports/dependency-analysis/build-health-report.txt).
