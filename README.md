## SkyStarter

---

### Table of Contents

- [Prerequisites](#prerequisites)
- [Running](#running)
    - [Local](#local)
    - [Run configuration](#run-configuration)
    - [Docker](#docker)
    - [JDK Setup](#jdk-setup)
- [Postman](#postman)
- [Open Api](#open-api)
    - [Generating OpenApi spec](#generating-openapi-spec)
- [Probes](#probes-actuator)
- [Code Formatter](#code-formatter)
    - [Line Separator](#line-separator)

---

## Prerequisites

Java version: `21`

---

## Running

If the service is run directly (not in Spring Cloud) endpoints which need Principal data require header:  
`Authorization`  
with an example value:

```
Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJfMlJudkN3M3g0dHhFWk1nOElZcVoyZFh1RHpkRERTNUdYN2ZENWg5a1ZBIn0.eyJleHAiOjE3MzI4MjUwNjgsImlhdCI6MTczMjgyNDc2OCwiYXV0aF90aW1lIjoxNzMyODIwMDQ5LCJqdGkiOiI5ZTY3MWMxMS04ZmFhLTRlZDgtYTNmYy1hNzI3NjlhNzIyY2IiLCJpc3MiOiJodHRwczovL2tleWNsb2FrOjk0NDMvcmVhbG1zL3BoYXJtYSIsImF1ZCI6ImFjY291bnQiLCJzdWIiOiI1ZGExNWQyZC0yYzBjLTQ2MWEtOTlhOS1iNDQ5ZDllYzExYzgiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJwaGFybWFBcHAtY2xpZW50Iiwic2lkIjoiNThkZGRjN2YtMTk2ZC00ODRhLWJiMzctNWRjOWM0YjUzNWVlIiwiYWNyIjoiMCIsImFsbG93ZWQtb3JpZ2lucyI6WyJodHRwOi8vbG9jYWxob3N0Ojg4ODgiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbIlJPTEVfVVNFUiIsIm9mZmxpbmVfYWNjZXNzIiwidW1hX2F1dGhvcml6YXRpb24iLCJkZWZhdWx0LXJvbGVzLXBoYXJtYSJdfSwicmVzb3VyY2VfYWNjZXNzIjp7ImFjY291bnQiOnsicm9sZXMiOlsibWFuYWdlLWFjY291bnQiLCJtYW5hZ2UtYWNjb3VudC1saW5rcyIsInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoib3BlbmlkIHByb2ZpbGUgZW1haWwiLCJlbWFpbF92ZXJpZmllZCI6ZmFsc2UsIm5hbWUiOiJMdWtrIFRlc3QiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJsdWtrIiwiZ2l2ZW5fbmFtZSI6Ikx1a2siLCJmYW1pbHlfbmFtZSI6IlRlc3QiLCJlbWFpbCI6Imx1a2tAdGVzdC5jb20ifQ.CBSZsNjbKvNMXt8_MY0aMBsK7_ify3gAdRCsNDgrnpqP1vhFP4OkD71cmP2wBOO7QJIBxOXLWzGcApkshqGJVsFBmJ7cfCi65Dyi1SCugGSWjr9b3cYxBiTu4S46DvM7G-Bdagj-LfWrZlUWpketSZtQWmSzyR-cBYn9v8J45ONNYR03EAJiW_Y4i6yhMm_ywv5Z7vgZX1v5PorRCxQlHM1Q6tyG940Jqvz1NDlyo7blYORmDSmu8eie_hBvWQrTlRLgBkh4eZhrwvmQJusHeRF7E3JPp_lHF66p0ohRHJ6E7mTr1YE8RiEfU8ke0XXU1FCKEgEitEzBeDQ4rPoi0g
```

### Local

To run locally as separate application, it requires to use `local` spring profile.   
Which can be added to the Gradle run command  
as arg:

```shell
--args='--spring.profiles.active=local'
```

added as JVM argument:

```shell
-Dspring.profiles.active=local
```

or setup as env variable:

```shell
SPRING_PROFILES_ACTIVE=local
```

### Run configuration

In `./run/` folder there are configured ready to use run configurations for IntelliJ IDEA.

There are configuration for:

- local

### Terminal commands

```shell
./gradlew bootRun --args='--spring.profiles.active=local'
```

or

```shell
./gradlew bootRun -Dspring.profiles.active=local
```

### Docker

Remember to change docker tags !

Build Dockerfile (terminal in project root)

```shell
  docker build -t sky-starter:latest . 
```

without cache:

```shell
  docker build --no-cache -t sky-starter:latest .
```

Create docker network:

```shell
   docker network create sky-network
```

Run it:

```shell
  docker run -d --name sky-starter --network sky-network -p 7979:7979 sky-starter:latest
```

### JDK Setup

This project uses Gradle Toolchains to ensure a consistent development environment. The required JDK version (Java 21)
is specified in the build.gradle.kts file.

If the correct JDK is not found on your local machine, Gradle will automatically download it using the Foojay Toolchain
Resolver. No manual JDK installation or JAVA_HOME configuration is needed to build the project.

JDK Download Location
The downloaded JDK will be stored in the Gradle user home directory:  
Windows:

```
C:\Users\<YourUsername>\.gradle\jdks\
```

macOS/Linux:

```
~/.gradle/jdks/
```

---

## Postman

collection (v2.1) is exported in:

[docs](./docs/postman/)

---

## Open API

UI:

```
http://localhost:7777/openapi/swagger-ui.html
```

Json docs:

```
http://localhost:7777/openapi/v3/api-docs
```

Yaml docs:

```
http://localhost:7777/openapi/v3/api-docs.yaml
```

config:

```
http://localhost:7777/openapi/v3/api-docs/swagger-config
```

### Generating OpenApi spec

```shell
    ./gradlew generateOpenApiDocs
```

View spec:
[openapi.yaml](./docs/api/openapi.yaml)

---

## Probes (Actuator)

Endpoints (GET):

```
http://localhost:7777/actuator
```

```
http://localhost:7777/actuator/info
```

```
http://localhost:7777/actuator/health
```

```
http://localhost:7777/actuator/health/{path}
```

where  
`{path}` - is a path from `/actuator/health` endpoint

example

```
http://localhost:7777/actuator/health/readiness
```

---

## Code formatter

Default for IntelliJ IDEA is used, exported can be found in:

[DefaultFormatting.xml](./DefaultFormatting.xml)

### Line separator

Use Unix line separator due to possible complication with Docker and deployment when using Windows CRLF  
To change in Intellij:

`Settings` → `Editor` → `Code Style` : `Line separator` - `Unix and macOS (\n)`

Change in git on windows:

```
git config --global core.autocrlf input
```

on Linux/macOs it can be disabled:

```
git config --global core.autocrlf false
```

Anyway a project has this setup in `.gitattribute` file.

---

## OWASP Dependency Check

Check dependencies for OWASP CVE security vulnerabilities

```shell
./gradlew dependencyCheckAggregate
```

View report:
[dependency-check-report.html](./build/reports/dependency-check-report.html)

For faster and unblocked check, request a free api key at:
https://nvd.nist.gov/developers/request-an-api-key

then add it in:
[gradle.properties](./gradle.properties)

```properties
nvdApiKey=<api-key>
```

---

## Dependency Analysis

Analyze dependencies graph

```shell
./gradlew buildHealth
```

View report:
[build-health-report.txt](./build/reports/dependency-analysis/build-health-report.txt)

