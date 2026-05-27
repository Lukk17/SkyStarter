## Context

The application is now deployable: Spring Boot 4 / Java 25 / Axon 5, Liquibase-managed schema, Postgres + Mongo Testcontainers IT green, distroless-ready Dockerfile (today using `eclipse-temurin:25-jre-alpine`). The maintainer's deployment target is generic Kubernetes — any cloud, any operator's cluster — with a strong "the operator owns the platform pieces, the chart owns the application" stance.

We need a Helm chart that:
- Ships in this repo so forks can lift it cleanly into their own service.
- Doesn't pull in any opinionated platform components (no Bitnami subcharts, no in-cluster Postgres / Mongo, no embedded Sealed Secrets).
- Plays nicely with the platform shape the maintainer already standardised on:
  - **Traefik 3** as the only ingress, configured with **Gateway API** CRDs.
  - External managed databases.
  - **GitHub Actions = CI**, **ArgoCD = CD**.
  - **Cosign keyless** image signing using GitHub OIDC.
  - **Kyverno** for admission-time signature verification.
  - **kube-prometheus-stack + OpenTelemetry Collector + Loki/Tempo** for observability — installed once per cluster, consumed by every chart.

Stakeholders: maintainer (template owner), platform team (cluster prerequisites), service operators (deploy this chart with their own values).

## Goals / Non-Goals

**Goals:**
- A working `deploy/helm/sky-starter/` chart that an operator can `helm install` against any cluster meeting the documented prerequisites.
- Pre-install / pre-upgrade Liquibase migration Job, blocking app rollout until migrations succeed.
- HTTP routing via a `HTTPRoute` referencing an operator-owned `Gateway`.
- Hardened Dockerfile: non-root, OCI labels, no shell / no package manager (or smallest possible JRE base if distroless conflicts with the Keycloak truststore).
- GitHub Actions workflow that builds + scans + SBOMs + cosign-keyless-signs + bumps a manifests tag.
- Sample ArgoCD `Application` template under `deploy/argocd/`.
- Sample Kyverno verifyImages policy under `deploy/kyverno/`.
- ADR-0011 + arc42 §7 + §11 + `docs/deployment.md`.

**Non-Goals:**
- No KEDA event-store-lag autoscaler. HPA on CPU + memory only.
- No Service Mesh (Istio / Linkerd).
- No cert-manager wiring inside the chart. The operator's `Gateway` already terminates TLS.
- No Bitnami / Helm-charts.io subcharts for databases.
- No in-chart secret management (Sealed Secrets, External Secrets, SOPS, Vault).
- No multi-cluster federation, no canary / blue-green orchestration. Standard rolling-update strategy.
- No GitOps tool other than ArgoCD documented in this change (Flux works with the same chart but is a follow-up doc).
- No cluster bootstrap (Traefik / Prometheus / Loki / etc. installation) — operator owns those.

## Decisions

### D1. Chart layout

```
deploy/helm/sky-starter/
├── Chart.yaml                          # apiVersion: v2, no dependencies
├── values.yaml                         # documented defaults
├── values-dev.yaml                     # local-cluster overrides
├── values-prod.yaml                    # production-shape example
├── README.md                           # chart-level operator notes
└── templates/
    ├── _helpers.tpl                    # name / fullname / labels
    ├── serviceaccount.yaml             # dedicated SA, never default
    ├── configmap-app.yaml              # application-k8s.yaml overlay
    ├── deployment.yaml                 # 1 container, probes, resource limits, securityContext
    ├── service.yaml                    # ClusterIP on app port
    ├── httproute.yaml                  # Gateway API, references operator-owned Gateway
    ├── servicemonitor.yaml             # gated by metrics.enabled
    ├── networkpolicy.yaml              # default-deny + explicit allows; gated
    ├── poddisruptionbudget.yaml        # minAvailable: 1
    ├── hpa.yaml                        # gated by autoscaling.enabled
    ├── job-migrations.yaml             # pre-install/pre-upgrade Helm hook
    └── tests/                          # helm test stubs (smoke health probe)
```

The chart does not include `ingress.yaml` (legacy `Ingress` resource) — Gateway API only. If a future fork wants nginx-ingress, that's a chart fork concern.

### D2. Migration Job runs the same image with a `migrate-only` profile

Rather than packaging Liquibase CLI + the JDBC driver as a separate image, we use the **app's own image** with a Spring profile that loads only the Liquibase auto-config and exits after migrations apply.

```yaml
spring:
  profiles:
    active: migrate-only
  main:
    web-application-type: NONE
  autoconfigure:
    exclude:
      # disable Mongo, Axon Server connector, the OAuth2 resource server, etc.
      - org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration
      - org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration
      - org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration
```

A small `MigrationApplicationListener` (or a `@Profile("migrate-only") @Bean ApplicationRunner`) calls `System.exit(0)` after Liquibase finishes.

**Why same-image-different-profile**: avoids a second Docker build + a second supply-chain artefact + a second Kyverno admission rule. The image is already trusted; reusing it for the Job is the path of least surprise.

**Alternatives considered**: Liquibase CLI in a small alpine image (rejected — second supply chain), `liquibaseUpdate` Gradle task in an init container (rejected — pulls Gradle into runtime, embarrassing).

### D3. HTTPRoute references an operator-owned Gateway

The chart emits an `HTTPRoute` whose `parentRefs` point at a `Gateway` the chart does **not** create:

```yaml
parentRefs:
  - name: {{ .Values.gateway.name }}
    namespace: {{ .Values.gateway.namespace }}
    kind: Gateway
hostnames:
  - {{ .Values.gateway.host }}
```

This matches the multi-tenant pattern: one `Gateway` (with TLS, with the public DNS name) lives in `traefik` namespace, owned by the platform team; each application's chart adds its own `HTTPRoute`. cert-manager + the Gateway TLS are out of chart scope.

**Alternatives considered**: chart creates the `Gateway` itself (rejected — fights the multi-tenant model); chart uses `Ingress` instead (rejected — Gateway API is the future; Traefik 3 is happy with both but we pick the standard).

### D4. Dockerfile rewrite — distroless first, alpine fallback

The current `Dockerfile` uses `eclipse-temurin:25-jre-alpine` and runs `keytool` at runtime to import the Keycloak self-signed cert. The aspirational target is **`gcr.io/distroless/java25-debian12:nonroot`** because:
- Smaller attack surface (no shell, no package manager).
- Auditable: distroless images are signed by Google with reproducible-build provenance.
- Non-root by default (`nonroot` tag → UID 65532).

**The risk**: distroless has no `keytool` — we can't run the cert import in the runtime stage. The plan: do the cert import in the **build stage** (which has full JDK), then `COPY --from=builder /opt/java/openjdk/lib/security/cacerts` into the runtime stage. If that fails (e.g. cacerts location differs between JDK and JRE distros), fall back to `eclipse-temurin:25-jre-alpine` and document the choice in ADR-0011.

The Dockerfile gets:
- OCI labels (`org.opencontainers.image.source`, `revision`, `created`, `licenses`, `description`) — populated from build args at CI time.
- An explicit `USER 65532` directive.
- No `apk add curl`, no `HEALTHCHECK` line — Kubernetes probes the actuator directly.

### D5. CI workflow shape

`.github/workflows/ci.yml` triggers on `pull_request` and `push: branches: [main]`.

```
job: build-and-test
  - checkout
  - setup-jdk @ 25 (Adoptium)
  - cache gradle
  - ./gradlew clean build           # full unit + IT suite (Testcontainers needs Docker; GHA runners have it)
  - ./gradlew dependencyCheckAggregate

job: image (needs: build-and-test, only on push-to-main and tags)
  - checkout
  - setup-buildx
  - login to GHCR (uses GITHUB_TOKEN with packages:write)
  - build runtime image, tag with ${{ github.sha }} + latest
  - syft .  → CycloneDX SBOM JSON
  - push image
  - cosign attach sbom <image> --sbom sbom.json
  - cosign sign --yes <image>      # OIDC token from id-token: write
  - bump deploy/helm/sky-starter/values-prod.yaml `image.tag` to ${{ github.sha }}, commit + push
```

**Permissions**: `id-token: write` (cosign OIDC), `packages: write` (GHCR), `contents: write` (manifest commit). No third-party auth secret.

**Manifest-bump strategy**: for the template, we keep both the chart and the prod values inside this repo for simplicity. A real fork can swap to a separate `*-deploy` repo by changing one workflow step. ADR-0011 documents both flows.

### D6. Cosign keyless verification policy ships as a template

The chart cannot install Kyverno itself, but it ships a sample policy at `deploy/kyverno/verify-image-signatures.yaml` that operators copy into their cluster. Parameters: GitHub owner, workflow path. Verification points at Sigstore's public Fulcio + Rekor — no operator account anywhere.

```yaml
verifyImages:
  - imageReferences: ["ghcr.io/<owner>/sky-starter:*"]
    attestors:
      - entries:
          - keyless:
              issuer: "https://token.actions.githubusercontent.com"
              subject: "https://github.com/<owner>/SkyStarter/.github/workflows/ci.yml@refs/heads/main"
```

### D7. Observability — wiring without back-ends

The chart enables emission, never installs receivers.

**Metrics**: `ServiceMonitor` (kube-prometheus-stack CRD). Operator's Prometheus picks it up via its label selector. Application exposes `/actuator/prometheus` (already supported by `spring-boot-starter-actuator` + Micrometer Prometheus registry — added as `runtimeOnly` dep in the migrate apply step).

**Traces**: OTel Java agent injected via initContainer copy:

```yaml
initContainers:
  - name: otel-agent
    image: ghcr.io/open-telemetry/opentelemetry-java-instrumentation:latest
    command: ["cp", "/javaagent.jar", "/agent/javaagent.jar"]
    volumeMounts: [{ name: agent-volume, mountPath: /agent }]

containers:
  - name: app
    env:
      - { name: JAVA_TOOL_OPTIONS, value: "-javaagent:/agent/javaagent.jar" }
      - { name: OTEL_SERVICE_NAME, value: sky-starter }
      - { name: OTEL_EXPORTER_OTLP_ENDPOINT, value: http://otel-collector.observability:4317 }
      - { name: OTEL_RESOURCE_ATTRIBUTES, value: "k8s.namespace.name=$(POD_NAMESPACE),k8s.pod.name=$(POD_NAME)" }
    volumeMounts: [{ name: agent-volume, mountPath: /agent, readOnly: true }]
```

**Alternative considered**: bake the agent into the Dockerfile (rejected — couples agent version to image rebuilds; initContainer is the standard pattern).

**Logs**: app-side Logback config switches to a JSON encoder when `OBS_LOGS_JSON=true`. Cluster-side Vector / Promtail reads container stdout. Chart adds no sidecar.

### D8. NetworkPolicy — default-deny + per-dependency allows

When `networkPolicy.enabled=true`:

```yaml
spec:
  podSelector: { matchLabels: <selectorLabels> }
  policyTypes: [Ingress, Egress]
  ingress:
    - from: [{ namespaceSelector: { matchLabels: { kubernetes.io/metadata.name: traefik } } }]
      ports: [{ port: 7777 }]
  egress:
    - to: [{ namespaceSelector: { matchLabels: { kubernetes.io/metadata.name: kube-system } } }]
      ports: [{ port: 53, protocol: UDP }, { port: 53, protocol: TCP }]   # DNS
    - to: <postgres egress block>      # configurable via values
    - to: <mongo egress block>
    - to: <keycloak egress block>
    - to: <otel collector egress block>
```

Default `enabled: false` in `values-dev.yaml` (developers don't want to debug network policies); default `enabled: true` in `values-prod.yaml`.

### D9. PodDisruptionBudget + HPA

`PDB` with `minAvailable: 1` so during node drain the replica isn't taken down before a replacement is scheduled. Pairs with `replicaCount >= 2` for production (single-replica dev keeps PDB at `minAvailable: 0` so node drains aren't blocked — overridable).

`HPA` keeps it simple: `cpu: 70%`, `memory: 80%`. Defaults `enabled: false`. ADR-0011 notes that **KEDA-based event-store-lag autoscaling** is the right long-term answer for an event-sourced system but is deferred — implementation needs a KEDA `ScaledObject` + a Prometheus query against the Axon `aggregate_event_entry` size, which is best done with real workload data.

### D10. ADR-0011 + arc42 + README

- **NEW** `docs/architecture/decisions/0011-helm-deployment-topology.md`. Covers: Traefik + Gateway API choice, no-in-chart-secrets stance, external DBs only, GHA-CI + ArgoCD-CD split, distroless-or-alpine outcome (recorded after apply), KEDA deferral, Kyverno verification.
- **arc42 §7 (Deployment view)** — refresh: Kubernetes column added to the diagram, showing pod / Gateway / external dependencies / observability sinks.
- **arc42 §11 (Risks)** — new row: "Cluster prerequisites are externally owned" — chart can't validate that Traefik / Prometheus / OTel / Kyverno are correctly installed; documented as operator responsibility.
- **`docs/deployment.md`** — operator-focused page: prerequisites, install command, values reference, GHA + ArgoCD split, cosign verify, SBOM access.
- **README** — new "Deployment" row in the docs table.

## Risks / Trade-offs

- **Distroless ↔ Keycloak truststore**. Likely solvable via build-stage cert import + `cacerts` copy. If not, fallback to alpine + `keytool`. Documented in ADR-0011.
- **GitHub Actions Docker availability for Testcontainers IT.** GHA Linux runners ship Docker, so the Boot 4 / Axon 5 IT runs unchanged. macOS / Windows runners would need a different approach — out of scope.
- **Operator-owned `Gateway`.** If the operator's `Gateway` doesn't allow our namespace via `allowedRoutes.namespaces.from`, the chart's `HTTPRoute` is silently ignored. Mitigation: docs explicitly call this out; the sample `values-prod.yaml` references a `Gateway` whose `allowedRoutes.namespaces.from: All` (or a namespace selector that includes ours).
- **OCI labels populated from build args.** The Dockerfile takes them as `ARG` parameters; CI passes `--build-arg`s for `revision` and `created`. Without those, labels are empty strings — the image still works, just less queryable. Acceptable.
- **Kyverno verify-images policy is operator-installed.** If the operator forgets, signed and unsigned images both run. We don't have a way to enforce policy installation. Documented; mitigation is "make sure your platform team applies the template before pointing ArgoCD at us".
- **`migrate-only` profile is brittle.** If a future Spring Boot autoconfig auto-wires something we exclude (e.g. a new auto-config for Mongo that fires unless we add another exclusion), the migration Job fails to start. Mitigation: small targeted IT covering the migrate-only profile.
- **First push-to-main lands without ArgoCD installed.** CI commits a tag bump; nothing reconciles. No harm, just a no-op. Documented expectation.

## Migration Plan

This is greenfield — no migration of an existing deployment. The operator path:

1. Install cluster prerequisites (Traefik 3, Gateway API CRDs, kube-prometheus-stack, OTel Collector, Loki / Tempo, Kyverno, ArgoCD).
2. Apply the sample Kyverno policy (after editing the GitHub owner / repo).
3. Provision external Postgres + MongoDB + Keycloak.
4. Create the Secret(s) with credentials in their preferred way; reference them via `secretKeyRef` in their own `values-prod.yaml`.
5. Push to main → CI builds image, signs, commits tag bump.
6. Apply `deploy/argocd/Application-sky-starter.yaml` to ArgoCD.
7. ArgoCD syncs the chart; the migration Job runs; the app comes up.
8. Verify cosign signature manually (one-time):
   ```shell
   cosign verify ghcr.io/<owner>/sky-starter:<sha> \
     --certificate-identity-regexp 'https://github.com/<owner>/SkyStarter/' \
     --certificate-oidc-issuer 'https://token.actions.githubusercontent.com'
   ```

Rollback: ArgoCD's UI / `argocd app rollback`; or revert the manifests-repo commit. The chart's pre-install Job applies migrations forward only — Liquibase rollback for prod data is out of scope (we follow ADR-0009: forward-only changesets).

## Open Questions

These are answered during apply, not before:

- **Does distroless's `cacerts` location match the build stage's?** Resolved by trying it (ADR-0011 records the outcome).
- **Single-repo or split-repo for manifests?** Today the chart + prod values live in this repo; that's the simplest fork story. Document both flows in `docs/deployment.md`; pick single-repo as the default.
- **Should we ship a `helm test` for a smoke health probe?** Yes — `templates/tests/test-health.yaml` runs `wget -qO- http://<service>/actuator/health/readiness`. Trivial; included.
- **Does the OTel agent auto-propagate Spring Security context across virtual threads?** Likely yes (Java 25 + recent OTel agent has virtual-thread support); to verify in the IT or in a smoke deploy. Out of scope for the first revision.
