# Health probes (Spring Boot Actuator)

Actuator is exposed on the application port. Endpoints exposed in `application.yaml` are limited to `health`, `info`, `readiness`, `liveness`.

| Endpoint | URL |
|---|---|
| Index | <http://localhost:7777/actuator> |
| Info | <http://localhost:7777/actuator/info> |
| Health | <http://localhost:7777/actuator/health> |
| Liveness | <http://localhost:7777/actuator/health/liveness> |
| Readiness | <http://localhost:7777/actuator/health/readiness> |

Generic shape:

```
http://localhost:7777/actuator/health/{path}
```

…where `{path}` is any path advertised by `/actuator/health`.

The split between `liveness` and `readiness` is Kubernetes-friendly: orchestrators can use them as probe URLs in a Helm chart or pod spec. The `permitAll()` rule in `SecurityConfig` means probes don't need a Bearer token.
