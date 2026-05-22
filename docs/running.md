# Running SkyStarter

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| **JDK** | 25 (Adoptium / Temurin) | Auto-provisioned by Gradle's Foojay toolchain resolver — no manual install needed; downloaded into `~/.gradle/jdks/`. |
| **PostgreSQL** | 15+ | Event store. Default expected at `localhost:5432`, db `starter`, user `postgres`, password `local`. |
| **MongoDB** | 7+ | Read-side projection store. Default expected at `localhost:27017`, db `starter`. |
| **Keycloak** | 26+ (optional for `local` profile) | Bearer JWT authority. The `local` profile includes an offline JWT decoder so a live Keycloak isn't required for the happy path. |
| **Docker** | required for `./gradlew :app:integrationTest` (also picked up by `./gradlew check`) | Testcontainers spins up its own PostgreSQL + MongoDB on ephemeral ports; your local DBs do **not** conflict. The Gradle task sets `TESTCONTAINERS_REUSE_ENABLE=true` on the forked test JVM only, so containers persist across runs and the second IT loop is seconds, not ~30. No shell or home-dir setup needed. |

## JDK setup

The required JDK is declared in [`build.gradle.kts`](../build.gradle.kts) (`projectJavaVersion = 25`, vendor `ADOPTIUM`). [`settings.gradle.kts`](../settings.gradle.kts) applies the [Foojay toolchain resolver](https://github.com/gradle/foojay-toolchains): if a matching JDK is missing, Gradle downloads it on first build.

JDK download location:

- Windows: `C:\Users\<YourUsername>\.gradle\jdks\`
- macOS / Linux: `~/.gradle/jdks/`

No manual `JAVA_HOME` configuration is needed.

## Local run (recommended for development)

The application requires the `local` Spring profile for the offline JWT decoder and dev-friendly logging.

### Terminal

```shell
./gradlew bootRun --args='--spring.profiles.active=local'
```

or

```shell
./gradlew bootRun -Dspring.profiles.active=local
```

or via env var:

```shell
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

The service listens on **port 7777**.

### IntelliJ IDEA run configuration

[`.run/`](../.run/) ships ready-to-use configurations:

- `local` — runs the app with `--spring.profiles.active=local`.

## Authorization header

Endpoints under `/v1/starter/**` require an `Authorization: Bearer <jwt>` header. Under the `local` profile the JWT signature is **not verified** — any well-formed JWT with the correct realm-roles claim works. Example test token:

```
Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJfMlJudkN3M3g0dHhFWk1nOElZcVoyZFh1RHpkRERTNUdYN2ZENWg5a1ZBIn0...
```

(See the Postman collection in [`docs/postman/`](postman/) for a working example.)

## Docker

> Remember to bump the image tag for each release.

Build (terminal in project root):

```shell
docker build -t sky-starter:latest .
```

Without cache:

```shell
docker build --no-cache -t sky-starter:latest .
```

Run:

```shell
docker run -d \
    --name sky-starter \
    -p 7777:7777 \
    --add-host="keycloak.test:host-gateway" \
    sky-starter:latest
```

`--add-host="keycloak.test:host-gateway"` adds an entry to the *container's* internal `/etc/hosts` — not the host machine's hosts file. It maps the hostname `keycloak.test` to the special Docker keyword `host-gateway`, which resolves to the host machine's IP on the virtual Docker network (this is **not** `127.0.0.1`). The container can then reach a host-running Keycloak instance using the correct hostname for certificate validation, regardless of the host OS's hosts configuration.

The image is multi-stage:

- **build stage**: `eclipse-temurin:25-jdk` — runs `./gradlew :app:bootJar`.
- **runtime stage**: `eclipse-temurin:25-jre-alpine` — imports the Keycloak self-signed certificate into the JVM truststore (see [`certificates/`](../certificates/) and [`docs/certificates.md`](certificates.md)).

The default `CMD` is `--spring.profiles.active=docker`.
