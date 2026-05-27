## ADDED Requirements

### Requirement: Chart packaging

The repository SHALL ship a Helm chart at `deploy/helm/sky-starter/` that is generic, cluster-agnostic, and assumes nothing about the cloud provider or namespace.

#### Scenario: Chart is renderable in isolation

- **WHEN** an operator runs `helm template sky-starter deploy/helm/sky-starter -f values-prod.yaml`
- **THEN** the command MUST produce valid Kubernetes manifests
- **AND** the manifests MUST pass `kubectl apply --dry-run=client --validate=true` against any cluster with the documented prerequisites installed

#### Scenario: Lint passes

- **WHEN** CI runs `helm lint deploy/helm/sky-starter`
- **THEN** the chart MUST lint clean (zero ERROR-level findings; INFO/WARN findings are allowed if explicitly justified in a comment in `Chart.yaml`)

### Requirement: External datastores only

The chart SHALL NOT include or recommend in-cluster PostgreSQL or MongoDB. The chart consumes JDBC + MongoDB connection strings via environment variables only. How the operator runs those datastores (managed cloud service, separate operator chart, on-prem cluster) is out of chart scope.

#### Scenario: No subchart dependencies

- **WHEN** `Chart.yaml` is read
- **THEN** the `dependencies` array MUST be empty (or absent)
- **AND** no `subchart` for PostgreSQL / MongoDB / Redis / any datastore MAY appear under `deploy/helm/sky-starter/charts/`

### Requirement: Plain env-vars for credentials, no in-chart secret management

The chart SHALL surface all credential-bearing values (DB usernames + passwords, OIDC issuer URI, etc.) as plain `env:` entries on the Deployment. The chart MUST NOT introduce dependencies on Sealed Secrets, External Secrets Operator, SOPS, Vault sidecars, or any other secret-management tool.

#### Scenario: Operator brings their own secrets backend

- **WHEN** an operator wants credentials sourced from an external secret store
- **THEN** they MUST be free to either (a) set the values plainly in their values overlay, (b) wrap the chart in a parent chart that injects `secretKeyRef` env entries, (c) use any GitOps post-processing they prefer
- **AND** the chart itself MUST NOT make this decision for them

### Requirement: Ingress via Traefik + Gateway API

Inbound HTTP traffic SHALL be routed through a `Gateway` + `HTTPRoute` pair (Kubernetes Gateway API) implemented by Traefik 3.

#### Scenario: HTTPRoute is created, Gateway is referenced

- **WHEN** the chart is rendered with `gateway.host=api.example.com`, `gateway.name=public-gateway`, `gateway.namespace=traefik`
- **THEN** a `HTTPRoute` MUST be emitted with `parentRefs: [{ name: public-gateway, namespace: traefik }]`
- **AND** the route MUST match the host `api.example.com` and the path prefixes `/v1/starter`, `/openapi`, `/actuator/health`
- **AND** the route MUST forward to the chart's own `Service` on port 7777
- **AND** the chart MUST NOT create the `Gateway` resource itself (operator-owned shared resource)

### Requirement: Database migrations run before the app starts

The chart SHALL execute Liquibase migrations as a Helm pre-install / pre-upgrade hook Job. The application Deployment MUST NOT start until the Job completes successfully.

#### Scenario: Migrations succeed before pods come up

- **WHEN** `helm install` or `helm upgrade` runs against a populated database
- **THEN** the migration Job MUST execute first, with `helm.sh/hook: pre-install,pre-upgrade` and `helm.sh/hook-weight: -5`
- **AND** the Job MUST run the application image with `--spring.profiles.active=migrate-only` (a profile that loads Liquibase, applies pending changesets, and exits)
- **AND** the application Deployment MUST be created only after the Job's terminal phase reports `Succeeded`

#### Scenario: Migrations fail — install aborts

- **WHEN** the migration Job exits non-zero
- **THEN** the application Deployment MUST NOT be created
- **AND** `helm install` / `helm upgrade` MUST report the failure
- **AND** the operator's recovery path MUST be: fix the changeset, run `helm upgrade` again

### Requirement: Health probes and graceful shutdown

The chart SHALL configure `livenessProbe`, `readinessProbe`, and `startupProbe` to hit Spring Boot Actuator endpoints, and SHALL set `terminationGracePeriodSeconds` long enough for the Axon pooled processor to finish its current batch.

#### Scenario: Probes hit the right endpoints

- **WHEN** the Deployment is rendered
- **THEN** the container's `livenessProbe` MUST point at `/actuator/health/liveness`
- **AND** the `readinessProbe` MUST point at `/actuator/health/readiness`
- **AND** the `startupProbe` MUST point at `/actuator/health` with a `failureThreshold` that allows up to 60 seconds of startup time
- **AND** all three probes MUST be HTTP GET on the application port (default 7777), with no Authorization header

#### Scenario: Termination drains in-flight events

- **WHEN** the Deployment's `terminationGracePeriodSeconds` is rendered
- **THEN** it MUST default to at least 60 seconds
- **AND** the value MUST be overridable via `terminationGracePeriodSeconds` in `values.yaml`

### Requirement: Pod security defaults

Containers SHALL run as a non-root user with a read-only root filesystem and dropped capabilities. The Pod's `securityContext` and the container's `securityContext` SHALL set the corresponding fields.

#### Scenario: Container is non-root, read-only root, no privilege escalation

- **WHEN** the Deployment is rendered with default values
- **THEN** the container `securityContext` MUST include: `runAsNonRoot: true`, `readOnlyRootFilesystem: true`, `allowPrivilegeEscalation: false`, `capabilities.drop: ["ALL"]`, `seccompProfile.type: RuntimeDefault`
- **AND** the Pod `securityContext` MUST include: `runAsUser: 65532`, `fsGroup: 65532`, `runAsNonRoot: true`
- **AND** if the application needs writable directories (e.g. `/tmp`), they MUST be mounted as `emptyDir{}` volumes — not by relaxing `readOnlyRootFilesystem`

### Requirement: Network policy default-deny + explicit allows

When `networkPolicy.enabled=true` (default for prod overlay), the chart SHALL emit a `NetworkPolicy` with default-deny ingress + egress and explicit allow rules for the documented dependencies.

#### Scenario: Default-deny + per-dependency allows

- **WHEN** the chart is rendered with `networkPolicy.enabled: true`
- **THEN** a `NetworkPolicy` MUST be emitted that denies all ingress and all egress by default
- **AND** ingress MUST allow traffic only from the configured ingress controller namespace (default Traefik)
- **AND** egress MUST allow traffic to: PostgreSQL host(s) on port 5432, MongoDB host(s) on port 27017, Keycloak issuer URI host on port 443, the OpenTelemetry collector service, and DNS (port 53) in the cluster's `kube-system` namespace
- **AND** the egress allow lists MUST be configurable via `networkPolicy.egress.{postgres,mongodb,keycloak,otel}` in `values.yaml`

### Requirement: Observability — metrics, traces, logs

The chart SHALL expose Prometheus-scrapable metrics, emit OpenTelemetry traces to a configured collector, and emit JSON-formatted logs to stdout. The chart MUST NOT install the receiving back-ends.

#### Scenario: Metrics — ServiceMonitor

- **WHEN** the chart is rendered with `metrics.enabled: true`
- **THEN** a `ServiceMonitor` (kube-prometheus-stack CRD) MUST be emitted pointing at the application's `Service` on the path `/actuator/prometheus`
- **AND** the application's Spring profile MUST expose `prometheus` in `management.endpoints.web.exposure.include`

#### Scenario: Traces — OpenTelemetry Java agent

- **WHEN** the chart is rendered with `observability.tracing.enabled: true`
- **THEN** the Deployment MUST inject the OTel Java agent (either via initContainer copy or via a baked-in `JAVA_TOOL_OPTIONS=-javaagent:...`)
- **AND** environment variables `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_SERVICE_NAME`, `OTEL_RESOURCE_ATTRIBUTES` MUST be set from values

#### Scenario: Logs — JSON to stdout

- **WHEN** the chart is rendered with `observability.logs.json: true`
- **THEN** the application MUST be configured to use a JSON-encoded console appender (Logback `LogstashEncoder` or equivalent)
- **AND** the chart MUST NOT add a sidecar log forwarder — log shipping is the cluster's responsibility

### Requirement: Image build + supply-chain verification

The runtime container SHALL be a hardened, multi-stage image that runs as non-root in a minimal base. CI SHALL generate an SBOM and sign the image with cosign keyless before publishing.

#### Scenario: Hardened runtime image

- **WHEN** the `Dockerfile` is read
- **THEN** the runtime stage MUST use a distroless base (`gcr.io/distroless/java25-debian12:nonroot`) or, if distroless is incompatible with the Keycloak truststore handling, an alpine JRE base — and the choice MUST be recorded in ADR-0011
- **AND** the image MUST include OCI labels: `org.opencontainers.image.source`, `revision`, `created`, `licenses`, `description`
- **AND** the image MUST NOT contain a shell, package manager, or `curl` (when distroless)
- **AND** the image's USER directive MUST resolve to a non-root UID (default 65532)

#### Scenario: SBOM generated and attached

- **WHEN** CI builds an image
- **THEN** Syft MUST generate a CycloneDX-JSON SBOM
- **AND** the SBOM MUST be attached to the image as an OCI artifact via `cosign attach sbom` (or `oras` equivalent)

#### Scenario: cosign keyless signature

- **WHEN** CI publishes the image to the registry
- **THEN** the image MUST be signed with `cosign sign --yes <image>` using the GitHub OIDC token
- **AND** no Sigstore account, no Fulcio account, no managed signing key MAY be required

#### Scenario: Cluster admission verifies the signature

- **WHEN** the operator deploys the Kyverno policy template shipped under `deploy/kyverno/verify-image-signatures.yaml`
- **THEN** the cluster MUST refuse to admit any image not signed by `https://github.com/<owner>/SkyStarter/.github/workflows/ci.yml@refs/heads/main`
- **AND** the policy template MUST be parameterised by `<owner>` so forks can paste it in unchanged

### Requirement: GitOps split — CI builds, ArgoCD deploys

Continuous-integration tooling SHALL NOT reach into the cluster directly. CI's last step is a Git commit; CD reads from Git.

#### Scenario: CI does not run kubectl against the cluster

- **WHEN** the GitHub Actions workflow is read
- **THEN** the workflow MUST NOT contain `kubectl apply`, `helm install`, `helm upgrade`, or any command that authenticates to a Kubernetes API server
- **AND** the workflow's last step on push-to-main MUST be a Git commit that updates the image tag in a manifests repo (or in `deploy/helm/sky-starter/values-prod.yaml` for single-repo flow)

#### Scenario: ArgoCD reconciles from Git

- **WHEN** the operator copies `deploy/argocd/Application-sky-starter.yaml` and applies it to their ArgoCD-installed cluster
- **THEN** ArgoCD MUST sync the chart automatically when the manifests repo's tag bumps
- **AND** the Application MUST be configurable to either auto-sync or manual-sync

### Requirement: Documentation — operator hand-off

The chart SHALL be paired with a single operator-focused page that lists every cluster prerequisite, every value the chart consumes, and the GitOps flow.

#### Scenario: docs/deployment.md exists and is current

- **WHEN** the operator opens `docs/deployment.md`
- **THEN** they MUST find: cluster prerequisites (Traefik 3, Gateway API CRDs, kube-prometheus-stack, OTel Collector, Loki/Tempo, Kyverno), the Helm install command, the values reference, the GitHub Actions / ArgoCD split, the cosign verify command, where to fetch the SBOM
- **AND** the page MUST be linked from the root `README.md` documentation table

#### Scenario: ADR captures the decisions

- **WHEN** `docs/architecture/decisions/0011-helm-deployment-topology.md` is read
- **THEN** it MUST record: the choice of Traefik + Gateway API (over nginx-ingress / Istio), the choice to keep databases external, the no-in-chart-secrets stance, the GitHub Actions / ArgoCD split, the distroless + cosign supply-chain decision, and the reason for any deviation taken during apply (e.g. fallback to alpine if distroless + Keycloak truststore proves incompatible)
