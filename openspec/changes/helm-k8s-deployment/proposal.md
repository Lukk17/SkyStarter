## Why

Three preceding changes (`upgrade-spring-boot-4`, `replace-ddlauto-with-liquibase`, `upgrade-axon-5`) brought the application to a deployable shape: Spring Boot 4 / Java 25 / Axon 5, Liquibase-managed schema, full integration test green on Postgres + MongoDB Testcontainers. Until now the only documented runtime path has been `./gradlew bootRun` and a single-process `Dockerfile`. The template's stated goal is to bootstrap services that go to production — and "production" today means **Kubernetes**.

This change ships an opinionated, generic Helm chart that any fork can lift into its own cluster with one `values-prod.yaml`. It also wires the **CI/CD split** the platform already implies: GitHub Actions builds, scans, signs, and writes the resulting image tag to a manifests repo; **ArgoCD** watches that repo and reconciles the cluster. There is no `kubectl apply` in CI.

The brainstorm in chat already locked the deployment stack:

- **Traefik 3** as the ingress controller, configured via the **Kubernetes Gateway API** (`Gateway` + `HTTPRoute`).
- **External PostgreSQL and MongoDB** — the chart consumes connection strings via `env:` only, no Bitnami subcharts, no in-cluster stateful workloads.
- **No secrets management in the chart.** Plain `env:` in `values.yaml`; how operators populate values (SOPS, Vault sidecar, sealed-secrets, plain Secret refs) is out of chart scope.
- **Full observability triple**: metrics (Prometheus / Micrometer), traces (OpenTelemetry → Tempo or Jaeger), logs (stdout JSON → Loki / equivalent). The chart enables exposure (ServiceMonitor, OTel collector annotation, structured-log encoder); it does NOT install the back-ends.
- **CI/CD split**: GitHub Actions builds + scans + signs + pushes the image, then commits a tag bump to the manifests repo. **ArgoCD** is the only thing that reaches the cluster.
- **Image build**: hardened multi-stage Dockerfile (distroless runtime, non-root user, OCI labels), **Syft** generates an SBOM, **cosign keyless** (Sigstore + GitHub OIDC, no Sigstore account) signs the image. **Kyverno** policy in the cluster verifies the signature on admission.

## What Changes

### Chart

- **NEW** Helm chart at `deploy/helm/sky-starter/` with:
  - `Chart.yaml`, `values.yaml`, `values-dev.yaml`, `values-prod.yaml`.
  - `templates/deployment.yaml` — single replica by default, configurable; sets resource requests/limits, JVM `-XX:MaxRAMPercentage=75`, mounts a downward-API volume for the OpenTelemetry agent's resource attributes.
  - `templates/service.yaml`, `templates/serviceaccount.yaml` (dedicated SA, never the namespace default).
  - `templates/httproute.yaml` — Gateway API `HTTPRoute` matching the configured host and path prefix `/v1/starter` + `/openapi/**` + `/actuator/health`. The chart does **not** create the cluster `Gateway`; that's an operator-owned shared resource.
  - `templates/servicemonitor.yaml` — kube-prometheus-stack-style scrape target on the `/actuator/prometheus` endpoint, gated by `metrics.enabled`.
  - `templates/networkpolicy.yaml` — default-deny ingress + egress, explicit allows for: ingress controller → app on port 7777; app → external Postgres / Mongo / Keycloak hostnames (configurable as a list); app → OTel collector. Gated by `networkPolicy.enabled`.
  - `templates/poddisruptionbudget.yaml` — `minAvailable: 1` so rolling node maintenance can't take the only replica down.
  - `templates/hpa.yaml` — gated by `autoscaling.enabled`; CPU + memory targets only (KEDA event-source-lag autoscaler is a follow-up).
  - `templates/job-migrations.yaml` — pre-install / pre-upgrade Helm hook that runs `liquibaseUpdate` against the configured datasource. The hook waits for completion before the app pod starts.
  - `templates/configmap-app.yaml` — `application-k8s.yaml` overlay (Axon serializer, processor settings, MongoDB UUID representation, Spring profile activation, Liquibase changelog path, actuator exposure list). Mounted as a volume and referenced via `SPRING_CONFIG_ADDITIONAL_LOCATION`.
  - `templates/_helpers.tpl` — name / fullname / labels / selectorLabels.
- Generic chart values knobs: `image.repository`, `image.tag`, `image.pullPolicy`, `replicaCount`, `resources.{requests,limits}`, `env.{...}`, `service.port`, `gateway.{name,namespace,host,path}`, `metrics.enabled`, `networkPolicy.{enabled,egress.{postgres,mongodb,keycloak,otel}}`, `autoscaling.{enabled,minReplicas,maxReplicas,targetCpu,targetMemory}`, `migrations.enabled`, `pdb.enabled`, `serviceAccount.{create,name,annotations}`.

### CI (GitHub Actions)

- **NEW** `.github/workflows/ci.yml` — pull-request and push-to-main triggers:
  - Cache Gradle, run `./gradlew clean build` (full unit + IT suite).
  - Run `./gradlew dependencyCheckAggregate` and fail on new high-severity findings.
  - Build the runtime container (multi-stage, distroless), tag with `${git-sha}` and `pr-${number}` for PRs.
  - Generate SBOM via Syft (CycloneDX JSON), attach to the image as an OCI artifact.
  - On push-to-main: sign the image with **cosign keyless** using the GitHub OIDC token (no Sigstore account, no managed keys), then push to GHCR.
  - On push-to-main: commit an image-tag bump to the manifests repo (or to `deploy/helm/sky-starter/values-prod.yaml` in this repo if the operator chooses single-repo flow).
- **NEW** `Dockerfile` rewrite:
  - Build stage stays `eclipse-temurin:25-jdk` running `./gradlew :app:bootJar`.
  - Runtime stage moves to `gcr.io/distroless/java25-debian12:nonroot` (no shell, no package manager, runs as UID 65532).
  - Imports the Keycloak self-signed certificate via a multi-step approach that doesn't need `keytool` at runtime (cert is added in the build stage and the truststore is copied into the distroless layer). If the cert handling proves incompatible with distroless, fall back to `eclipse-temurin:25-jre-alpine` + `keytool` and document.
  - Adds OCI labels: `org.opencontainers.image.source`, `revision`, `created`, `licenses`, `description`.
  - Drops the `HEALTHCHECK` line + the `apk add curl` — k8s liveness/readiness probes hit the actuator directly.

### CD (ArgoCD)

- **NEW** `deploy/argocd/Application-sky-starter.yaml` — sample ArgoCD `Application` manifest pointing at `deploy/helm/sky-starter/` with a `values-prod.yaml` overlay. Documented as a **template** the operator copies into their ArgoCD `ApplicationSet` or `Application` manifest repo.

### Cluster pre-reqs (documented, not provisioned)

The chart assumes the cluster has these installed and configured by the platform team:

- **Traefik 3** as a `GatewayClass`-implementing controller.
- **Gateway API** CRDs (`Gateway`, `HTTPRoute`).
- A shared `Gateway` resource (TLS, public host) in a known namespace.
- **kube-prometheus-stack** (or any Prometheus-Operator-compatible stack) so the `ServiceMonitor` CRD is recognised.
- An **OpenTelemetry Collector** reachable in-cluster; the chart points the OTel Java agent at it via env.
- A log-shipping stack (Vector / Promtail) reading container stdout into Loki / equivalent.
- **Kyverno** with a `verifyImages` policy enforcing cosign-keyless signatures from the project's GitHub OIDC subject.

These prerequisites get a single page of documentation under `docs/deployment.md` so operators know what to install before pointing ArgoCD at the chart.

### Documentation

- **NEW** `docs/deployment.md` — quick reference: cluster prerequisites, how to consume the chart, env vars the chart expects, the GitHub Actions / ArgoCD split, the cosign verify command, where to find the SBOM.
- **NEW** ADR `docs/architecture/decisions/0011-helm-deployment-topology.md` — captures the deployment-stack decisions (Traefik + Gateway API, no in-chart secrets, external DBs only, CI/CD split, distroless + cosign).
- arc42 §7 (Deployment view) — refreshed: the existing diagram (`docs/architecture/diagrams/06-deployment-view.md`) gets a Kubernetes column showing pod / Gateway / external dependencies / observability sinks.
- arc42 §11 (Risks) — new row: "Cluster prerequisites are externally owned" — chart can't validate that Traefik / Prometheus / OTel / Kyverno are correctly installed; document operator responsibility.
- README — new "Deployment" row in the documentation table linking to `docs/deployment.md`.

### Non-goals

- **No in-cluster databases.** Postgres + Mongo are external (managed cloud or on-prem, operator's choice).
- **No Axon Server.** `axon.axonserver.enabled=false` stays.
- **No Helm chart for Traefik / Prometheus / Loki / Tempo / OTel / Kyverno / cert-manager.** The chart consumes them; the operator installs them.
- **No automatic Sealed-Secrets / SOPS / Vault wiring** — values live in plain `env:`. How the operator backs those values is out of scope.
- **No Service Mesh.** Istio / Linkerd are operator choices.
- **No Helm chart for the Liquibase migration runtime.** The pre-install Job uses the same image as the application and invokes `java -jar … --spring.profiles.active=migrate-only` (a new lightweight profile that runs Liquibase + exits).
- **No KEDA event-store-lag autoscaler.** Standard HPA on CPU + memory only; KEDA is a follow-up if real workloads demand it.

## Capabilities

### New Capabilities

- `kubernetes-deployment`: Defines what the Helm chart MUST produce, what cluster prerequisites it relies on, and how CI/CD interacts with it. Lives at `openspec/specs/kubernetes-deployment/spec.md`. Future deployment changes (KEDA autoscaler, cert-manager wiring, alternative ingress) update this spec via deltas.

### Modified Capabilities

(None.)

## Impact

- **Code**: `Dockerfile` rewrite (distroless, non-root, OCI labels, no curl).
- **New artefacts**:
  - `deploy/helm/sky-starter/` — full chart (~12 templates).
  - `deploy/argocd/Application-sky-starter.yaml` — sample.
  - `.github/workflows/ci.yml` — build + scan + SBOM + cosign + manifests bump.
  - `app/src/main/resources/application-migrate-only.yaml` — disables web/Axon/Mongo, enables Liquibase, runs migration and exits.
- **Docs**: `docs/deployment.md`, ADR-0011, arc42 §7 + §11 updates, README documentation-table row.
- **OpenSpec**: `kubernetes-deployment` capability added.
- **Build**: no Gradle changes — the chart consumes the existing `bootJar` artefact via the existing `Dockerfile`.
- **Tests**: no new automated tests in this change (a Helm linter step is included in CI; chart-rendering correctness is verified by `helm template` + `kubectl --dry-run=client apply -f -` in the CI workflow).
- **Security**: image runs as non-root in a distroless base; SBOM is generated and signed; cosign keyless verifies the supply chain at admission via Kyverno.
- **Risk**: Distroless + custom Keycloak cert handling may need iteration. Documented mitigation: fall back to `eclipse-temurin:25-jre-alpine` + the existing `keytool` flow if needed; ADR records whichever path is chosen.

After this lands, a fork can `helm install sky-starter deploy/helm/sky-starter -f values-prod.yaml` (or point ArgoCD at the chart) and have a running, observable, signed-image deployment.
