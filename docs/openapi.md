# OpenAPI & Postman

## Swagger UI / OpenAPI endpoints

When the app is running on `localhost:7777` (default port):

| Resource | URL |
|---|---|
| Swagger UI | <http://localhost:7777/openapi/swagger-ui.html> |
| OpenAPI JSON | <http://localhost:7777/openapi/v3/api-docs> |
| OpenAPI YAML | <http://localhost:7777/openapi/v3/api-docs.yaml> |
| Swagger config | <http://localhost:7777/openapi/v3/api-docs/swagger-config> |

## Generating the OpenAPI spec from the build

The [`org.springdoc.openapi-gradle-plugin`](https://plugins.gradle.org/plugin/org.springdoc.openapi-gradle-plugin) is wired in [`app/build.gradle.kts`](../app/build.gradle.kts). It boots the application with the `local` profile, scrapes the OpenAPI doc, and writes it to `docs/api/`:

```shell
./gradlew generateOpenApiDocs
```

Output: [`docs/api/openapi.yaml`](api/openapi.yaml).

## Postman

A Postman collection (v2.1) is exported in [`docs/postman/`](postman/). Import it via Postman's *File → Import* dialog. The collection includes a pre-baked Bearer JWT for the `local` profile so requests work out of the box against a locally-run service.
