# Module dependencies

Strict inward-only dependency rule between Gradle modules.

```mermaid
flowchart RL
    app["app<br/>(Spring Boot main)"]
    infra["infrastructure<br/>(adapters: REST, Mongo, security, mappers)"]
    svc["service<br/>(use-case orchestration)"]
    domain["domain<br/>(aggregate, events, ports — no Spring)"]

    app --> infra
    infra --> svc
    infra --> domain
    svc --> domain
```

The arrow direction is the *only* allowed direction. A new dependency the other way (e.g. `domain → infrastructure`) is a build-breaking violation of the architecture.
