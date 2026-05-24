# CI/CD

Two GitHub Actions workflows live under [`.github/workflows`](../.github/workflows).

## CI — `ci.yml`

Runs on every pull request and on pushes to `develop` / `master`. It sets up
Temurin 25, then runs the full gate:

```shell
./gradlew check
```

That covers unit tests, the Testcontainers integration suite (the runner's
Docker daemon backs Testcontainers), ArchUnit, and the JaCoCo coverage floor.
There is no OWASP dependency-check job — run it locally when you need it (see
[`code-quality.md`](code-quality.md)).

## Release — `release.yml`

A manual (`workflow_dispatch`) pipeline that asks for a version and publishes a
Docker image plus a GitHub release. Trigger it from the Actions tab, entering a
semver version such as `1.2.3` (a leading `v` is stripped).

It runs these steps in order and stops at the first failed guard:

1. Validate the version is `X.Y.Z`.
2. Fail if the GitHub release `v<version>` already exists.
3. Fail if Docker Hub already has `<user>/sky-starter:<version>`.
4. Run `./gradlew check`.
5. Build the image from [`Dockerfile`](../Dockerfile) and push
   `<user>/sky-starter:<version>` and `<user>/sky-starter:latest`.
6. Create the GitHub release `v<version>` with auto-generated notes.

The image repository name is `<DOCKERHUB_USERNAME>/sky-starter`; nothing is
hardcoded — the namespace comes from the secret.

## Required repository secrets

Set these under *Settings → Secrets and variables → Actions*. `GITHUB_TOKEN` is
provided automatically.

`DOCKERHUB_USERNAME` — your Docker Hub account/namespace.

`DOCKERHUB_TOKEN` — a Docker Hub access token with read/write scope (*Account
Settings → Security → New Access Token*), not your password.

## Cutting a release

Push the commit you want to release to its branch, then in the GitHub Actions
tab run the **Release** workflow against that branch and supply the version.
