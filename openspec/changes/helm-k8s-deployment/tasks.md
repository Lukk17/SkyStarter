## 1. `migrate-only` Spring profile

- [ ] 1.1 Add `app/src/main/resources/application-migrate-only.yaml`: `spring.main.web-application-type: none`, `spring.autoconfigure.exclude` covering Mongo + OAuth2 + Axon Server connector + Spring Modulith pieces we don't need for migrations, `spring.liquibase.enabled: true`, `spring.jpa.hibernate.ddl-auto: validate`. Mongo URI / Keycloak issuer URI not required.
- [ ] 1.2 Add a small `MigrateOnlyApplicationRunner` (or `@Profile("migrate-only") @Bean ApplicationRunner`) in `app/` that calls `System.exit(0)` after Liquibase finishes. Ensure it doesn't run under any other profile.
- [ ] 1.3 Smoke-test locally: `./gradlew :app:bootRun --args='--spring.profiles.active=migrate-only'` against an empty Postgres — confirm Liquibase applies both changesets and the JVM exits with code 0 in <30s.
- [ ] 1.4 Add a small IT (`MigrateOnlyIT`) in `app/src/test/`: starts the app under `migrate-only` profile against a Postgres Testcontainer, asserts the JVM exits 0 and `databasechangelog` has 2 rows.

## 2. Hardened Dockerfile

- [ ] 2.1 Rewrite `Dockerfile`: build stage stays `eclipse-temurin:25-jdk` (full JDK so `keytool` is available); runtime stage targets `gcr.io/distroless/java25-debian12:nonroot`.
- [ ] 2.2 Move the Keycloak self-signed cert import into the BUILD stage (where `keytool` exists). The build stage `RUN keytool -importcert ...` against `$JAVA_HOME/lib/security/cacerts`, then `COPY --from=builder /opt/java/openjdk/lib/security/cacerts /opt/java/openjdk/lib/security/cacerts` into the runtime stage. If the cacerts path differs between JDK and distroless JRE, pin a known location and document.
- [ ] 2.3 Add OCI labels (`org.opencontainers.image.{source,revision,created,licenses,description}`) populated from `ARG`s.
- [ ] 2.4 Drop `apk add curl` and the `HEALTHCHECK` line — distroless has neither, and Kubernetes probes the actuator directly.
- [ ] 2.5 Set `USER 65532` explicitly (the `nonroot` distroless tag already does this; explicit declaration documents intent).
- [ ] 2.6 If distroless + Keycloak truststore proves incompatible after iteration: fall back to `eclipse-temurin:25-jre-alpine` + the existing `keytool` flow, and record the deviation in ADR-0011.
- [ ] 2.7 Build locally: `docker build --build-arg=...=... -t sky-starter:dev .`. Confirm the image runs as UID 65532 (`docker run --rm sky-starter:dev id` — but that needs a shell; instead, deploy and read the pod's `securityContext.runAsUser`).

## 3. Helm chart skeleton

- [ ] 3.1 Create `deploy/helm/sky-starter/Chart.yaml` (apiVersion: v2, type: application, version: 0.1.0, appVersion: matching project version). No `dependencies:`.
- [ ] 3.2 Create `deploy/helm/sky-starter/values.yaml` with documented defaults for: `image.{repository,tag,pullPolicy}`, `replicaCount`, `resources.{requests,limits}`, `env: {}`, `service.port`, `gateway.{name,namespace,host}`, `metrics.enabled`, `observability.{tracing.enabled,logs.json}`, `networkPolicy.{enabled,egress.{postgres,mongodb,keycloak,otel}}`, `autoscaling.{enabled,minReplicas,maxReplicas,targetCpu,targetMemory}`, `migrations.enabled`, `pdb.{enabled,minAvailable}`, `serviceAccount.{create,name,annotations}`, `terminationGracePeriodSeconds`.
- [ ] 3.3 Create `deploy/helm/sky-starter/values-dev.yaml`: single replica, `metrics.enabled: false`, `networkPolicy.enabled: false`, `autoscaling.enabled: false`. Suitable for a kind/minikube cluster with a local Postgres + Mongo.
- [ ] 3.4 Create `deploy/helm/sky-starter/values-prod.yaml`: `replicaCount: 2`, `metrics.enabled: true`, `networkPolicy.enabled: true`, `autoscaling.enabled: true` (`minReplicas: 2`, `maxReplicas: 10`), `pdb.enabled: true`, all egress allow-lists pre-populated with placeholder hosts the operator must override.
- [ ] 3.5 Create `templates/_helpers.tpl` with `name`, `fullname`, `chart`, `labels`, `selectorLabels`, `serviceAccountName` template helpers (standard Helm boilerplate).
- [ ] 3.6 Add chart-level `README.md` with: short summary, install command, link to `docs/deployment.md`.

## 4. Helm chart templates

- [ ] 4.1 `templates/serviceaccount.yaml` — gated by `.Values.serviceAccount.create`. Annotations from values (e.g. for IRSA on EKS).
- [ ] 4.2 `templates/configmap-app.yaml` — `application-k8s.yaml` overlay containing Axon serializer (jackson), processor mode (pooled), MongoDB UUID representation, Spring profile (`k8s` plus the user's overlay), Liquibase changelog path, actuator `prometheus` exposed (additive over `application.yaml`'s base list).
- [ ] 4.3 `templates/deployment.yaml`:
  - Single container, port 7777.
  - `resources` from values.
  - `securityContext` (container): `runAsNonRoot: true`, `readOnlyRootFilesystem: true`, `allowPrivilegeEscalation: false`, `capabilities.drop: ["ALL"]`, `seccompProfile.type: RuntimeDefault`.
  - `securityContext` (pod): `runAsUser: 65532`, `fsGroup: 65532`.
  - `livenessProbe: /actuator/health/liveness`, `readinessProbe: /actuator/health/readiness`, `startupProbe: /actuator/health` with generous failure threshold.
  - `terminationGracePeriodSeconds` from values.
  - Mounts the configmap as `/config/application-k8s.yaml`; sets `SPRING_CONFIG_ADDITIONAL_LOCATION` to that path.
  - Mounts `emptyDir{}` at `/tmp` (read-only root filesystem otherwise breaks JVM tempdir).
  - Loads `env:` from values verbatim.
  - **OTel initContainer + agent volume mount** when `observability.tracing.enabled: true`.
- [ ] 4.4 `templates/service.yaml` — `ClusterIP`, port 7777, named port `http`.
- [ ] 4.5 `templates/httproute.yaml` — Gateway API `HTTPRoute`. `parentRefs` from values; hostnames from values; rules match path prefixes `/v1/starter`, `/openapi`, `/actuator/health`; backendRefs to the service.
- [ ] 4.6 `templates/job-migrations.yaml` — Helm pre-install/pre-upgrade hook (`helm.sh/hook: pre-install,pre-upgrade`, weight `-5`, hook-delete-policy `before-hook-creation`). Same image as the app, args `--spring.profiles.active=migrate-only`. Resources from values; same securityContext as the app.
- [ ] 4.7 `templates/servicemonitor.yaml` — gated by `.Values.metrics.enabled`. Selector matches the chart's labels; endpoint `/actuator/prometheus` on port `http`.
- [ ] 4.8 `templates/networkpolicy.yaml` — gated by `.Values.networkPolicy.enabled`. Default-deny + per-dependency allows from `.Values.networkPolicy.egress.{postgres,mongodb,keycloak,otel}`. Ingress from configured ingress controller namespace.
- [ ] 4.9 `templates/poddisruptionbudget.yaml` — gated by `.Values.pdb.enabled`. `minAvailable` from values.
- [ ] 4.10 `templates/hpa.yaml` — gated by `.Values.autoscaling.enabled`. CPU + memory targets.
- [ ] 4.11 `templates/tests/test-health.yaml` — Helm test pod that runs `wget -qO- http://<service>:7777/actuator/health/readiness | grep '"status":"UP"'`.

## 5. Lint + render verification

- [ ] 5.1 Locally: `helm lint deploy/helm/sky-starter` — no ERROR-level findings.
- [ ] 5.2 Locally: `helm template sky-starter deploy/helm/sky-starter -f deploy/helm/sky-starter/values-prod.yaml | kubectl apply --dry-run=client --validate=true -f -` — every manifest validates against the cluster's API. Use a kind cluster with the Gateway API + ServiceMonitor CRDs installed for full validation; otherwise skip the failing API-resource validations and document.
- [ ] 5.3 Render with both dev and prod overlays; eyeball-diff to confirm gating works (NetworkPolicy + ServiceMonitor + HPA appear only in prod; they are absent in dev).

## 6. ArgoCD + Kyverno samples

- [ ] 6.1 `deploy/argocd/Application-sky-starter.yaml` — sample `Application` manifest pointing at `deploy/helm/sky-starter/` with `values-prod.yaml`. Include comments instructing the operator to swap the repo URL + revision.
- [ ] 6.2 `deploy/kyverno/verify-image-signatures.yaml` — `ClusterPolicy` with `verifyImages` rule. Subject regex parameterised on the GitHub owner. Issuer fixed at `https://token.actions.githubusercontent.com`. Document the regex syntax that lets the operator paste their owner in.

## 7. GitHub Actions CI

- [ ] 7.1 `.github/workflows/ci.yml`:
  - `name: CI`, on push (main + tags) + pull_request.
  - `permissions: { contents: write, id-token: write, packages: write }`.
  - `job: build-and-test` — runs on `ubuntu-latest`, sets up JDK 25 (Temurin, Foojay), caches Gradle, runs `./gradlew clean build dependencyCheckAggregate`.
  - `job: image` (needs build-and-test, only on push-to-main and tags):
    - `docker/setup-buildx-action`.
    - `docker/login-action` to GHCR (uses GITHUB_TOKEN).
    - Build the image; tag with `${{ github.sha }}` + `latest` (only on main).
    - `anchore/sbom-action` (Syft) → CycloneDX-JSON.
    - Push image, then `cosign attach sbom`.
    - `cosign sign --yes ghcr.io/<owner>/sky-starter@${digest}`.
    - Bump `image.tag` in `deploy/helm/sky-starter/values-prod.yaml`, commit + push (uses `peter-evans/create-pull-request` or a direct commit; pick direct commit for simplicity, document).
- [ ] 7.2 Add a `helm-lint` job that runs `helm lint deploy/helm/sky-starter` on every push + PR. Also runs `helm template ... | kubectl --dry-run=client apply -f -` against the sample values.

## 8. Documentation

- [ ] 8.1 `docs/deployment.md` — operator quick-reference: prerequisites table (Traefik 3 + Gateway API + kube-prometheus-stack + OTel Collector + Loki/Tempo + Kyverno + ArgoCD), `helm install` command, values reference (one row per value), CI/CD split explanation, cosign verify command, SBOM access (`cosign download sbom`), troubleshooting (HPA not scaling = no Prometheus metrics, HTTPRoute silently ignored = Gateway `allowedRoutes` mismatch, etc.).
- [ ] 8.2 `docs/architecture/decisions/0011-helm-deployment-topology.md` — full ADR. Records: Traefik + Gateway API choice, no-in-chart-secrets, external DBs only, GHA-CI + ArgoCD-CD split, distroless-or-alpine outcome (after apply), KEDA deferral, Kyverno verification. Reference design doc decisions D1–D10.
- [ ] 8.3 Update `docs/architecture/arc42/arc42.md` §7 (Deployment view): add a Kubernetes column describing the pod / Gateway / external dependencies / observability sinks.
- [ ] 8.4 Update arc42 §9 (ADR list): add 0011 link.
- [ ] 8.5 Update arc42 §11 (Risks): add row "Cluster prerequisites are externally owned; chart cannot validate operator setup".
- [ ] 8.6 Update `docs/architecture/diagrams/06-deployment-view.md`: add a Kubernetes-flavoured topology block, mirroring the platform stack.
- [ ] 8.7 Update root `README.md` documentation table: add a 🚀 **Deployment (Helm + ArgoCD)** row pointing at `docs/deployment.md`.

## 9. Final verification

- [ ] 9.1 `./gradlew clean build` — full unit + IT suite green (the new `migrate-only` profile must not break any existing test).
- [ ] 9.2 `helm lint deploy/helm/sky-starter` clean.
- [ ] 9.3 `helm template ... | kubectl --dry-run=client apply -f -` clean against a cluster with the Gateway API + ServiceMonitor CRDs.
- [ ] 9.4 Smoke-deploy on a kind/minikube cluster:
  - Install Traefik 3 + Gateway API CRDs, Prometheus Operator, OTel Collector.
  - Run an external Postgres + Mongo (Docker).
  - `helm install sky-starter deploy/helm/sky-starter -f deploy/helm/sky-starter/values-dev.yaml --set image.repository=... --set image.tag=...`.
  - Confirm: migration Job completes, deployment becomes Ready, an `HTTPRoute` is bound, `curl http://<gateway-ip>/v1/starter/<uuid>` returns the expected 404 (or 401 if security is on), Prometheus scrapes metrics, OTel collector receives spans.
- [ ] 9.5 GitHub Actions CI green on a test branch (note: actual cosign signing only fires on push-to-main; verify the workflow YAML compiles via `actionlint` or by the GHA UI).
- [ ] 9.6 Cosign verify: `cosign verify <image>` succeeds against the Sigstore public Fulcio + Rekor.
- [ ] 9.7 ADR-0011 committed; arc42 + diagrams + README updated; `docs/deployment.md` written.

## 10. Cleanup + archive

- [ ] 10.1 Remove any `// TODO helm-k8s-deployment` markers introduced during the work.
- [ ] 10.2 Squash WIP commits into a logical sequence — suggested splits:
  - **A**: Dockerfile rewrite + `migrate-only` profile.
  - **B**: Helm chart (deploy/helm/, deploy/argocd/, deploy/kyverno/).
  - **C**: GitHub Actions workflow.
  - **D**: Docs (ADR-0011, arc42, deployment.md, README row).
- [ ] 10.3 Run `/opsx:archive helm-k8s-deployment` to move the change to `openspec/changes/archive/<YYYY-MM-DD>-helm-k8s-deployment/` and (since the change ADDS a new capability with no MODIFIED deltas) sync `openspec/specs/kubernetes-deployment/spec.md`.
