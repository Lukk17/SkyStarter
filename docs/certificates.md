# Local Keycloak certificates

The Docker image imports a self-signed Keycloak certificate into the JVM truststore (see the runtime stage of [`Dockerfile`](../Dockerfile)). The certificate files live under [`certificates/localhost/`](../certificates/localhost/):

- `localhost.cnf` — OpenSSL configuration (subject, SAN entries).
- `localhostDomain.crt` — the certificate.
- `localhostDomain.key` — the private key.

## Generating a fresh certificate

If you need to regenerate the certificate (e.g. after rotating the dev Keycloak host name), run from the project root:

```shell
openssl req -x509 -nodes -days 3650 \
    -key ./certificates/localhost/localhostDomain.key \
    -out ./certificates/localhost/localhostDomain.crt \
    -config ./certificates/localhost/localhost.cnf \
    -extensions req_ext
```

`localhost.cnf` already contains every SAN / DN entry the Keycloak `keycloak.test` host needs; OpenSSL just needs to be invoked from the directory shown above so paths resolve.

After regenerating, rebuild the Docker image so the new cert lands in the runtime truststore:

```shell
docker build --no-cache -t sky-starter:latest .
```
