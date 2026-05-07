# 0005 — Keycloak OIDC for authentication

- **Status:** Accepted
- **Date:** 2025-11-15
- **Deciders:** Project maintainer
- **Tags:** security, oidc, keycloak

## Context and problem statement

Services in this estate authenticate users against a shared Keycloak realm. Each service is a stateless API behind an API gateway and never owns user credentials. We need:

- Bearer-JWT validation against Keycloak's JWKS,
- Realm roles surfaced as Spring authorities,
- Method-level authorisation (`@PreAuthorize`),
- A way to develop locally without a live Keycloak.

## Decision drivers

- Single sign-on with the rest of the platform.
- Standard OIDC, no custom protocols.
- Offline development must be possible.

## Considered options

1. **Keycloak OIDC + Spring Security `oauth2ResourceServer`** with profile-split decoders.
2. **Spring Authorization Server** (we'd run our own).
3. **Custom JWT verification** with a hand-rolled filter.

## Decision

We chose **option 1**:

- Production: `SecurityConfig` (`@Profile("!local & !test")`) wires `JwtDecoders.fromIssuerLocation(issuerUri)` and the realm-roles converter.
- Local: `LocalSecurityConfig` (`@Profile("local")`) installs an offline JWT decoder (`KeycloakAuthenticationConverter::parseOfflineToken`) that does **not** verify signatures — for development convenience only.
- Test: neither config is active; integration tests bring in `TestSecurityConfig` plus `@WithMockUser`.
- Roles are extracted from `realm_access.roles` by `KeycloakAuthenticationConverter` and exposed as Spring `GrantedAuthority`. Method security is enabled via `@EnableMethodSecurity`.

## Consequences

### Positive

- Standards-based authentication; integrates with the platform IdP without bespoke glue.
- `@PreAuthorize("hasAnyRole('USER','ADMIN')")` reads naturally and is enforced at the method boundary.
- Local development continues to work even with no Keycloak running.

### Negative / accepted trade-offs

- The local profile decodes JWTs without signature verification — **catastrophic if it ever runs in production**. The profile name and the production profile guard `!local & !test` are the only fences. Documented explicitly in arc42 §11.
- Realm-role-as-authority assumes Keycloak realm roles only — composite or client roles need the converter to be extended.
- CSRF/CORS are disabled because the API is meant to live behind a gateway. Browser-direct callers would need a different config.

## Links

- [arc42 §8.3](../arc42/arc42.md#83-security)
