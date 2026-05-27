package com.lukksarna.skystarter.infrastructure.config.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class KeycloakAuthenticationConverter {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";

    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    public static JwtAuthenticationConverter getJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt ->
                Optional.ofNullable(jwt.getClaimAsMap(REALM_ACCESS_CLAIM))
                        .map(realmAccess -> realmAccess.get(ROLES_CLAIM))
                        .filter(Collection.class::isInstance)
                        .map(obj -> (Collection<?>) obj)
                        .orElse(Collections.emptyList())
                        .stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                        .toList()
        );
        return converter;
    }

    /**
     * Decodes a JWT WITHOUT verifying its signature, expiry, or issuer. For the
     * {@code local} profile only, where no live Keycloak is available. NEVER use
     * this as a production {@link org.springframework.security.oauth2.jwt.JwtDecoder}
     * -- it trusts any well-formed token. Production uses
     * {@code JwtDecoders.fromIssuerLocation(...)} which validates the signature.
     */
    public static Jwt parseUnsafeOfflineToken(String token) {
        try {
            String[] parts = token.split("\\.");
            String headerPayload = new String(URL_DECODER.decode(parts[0]), StandardCharsets.UTF_8);
            String payload = new String(URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);

            Map<String, Object> headers = JSON_MAPPER.readValue(headerPayload, MAP_TYPE);
            Map<String, Object> claims = JSON_MAPPER.readValue(payload, MAP_TYPE);

            Jwt.Builder jwtBuilder = Jwt.withTokenValue(token);

            headers.forEach(jwtBuilder::header);
            parseTokenClaims(claims, jwtBuilder);

            return jwtBuilder.build();

        } catch (Exception e) {
            throw new JwtException("Failed to decode JWT for local development", e);
        }
    }

    private static void parseTokenClaims(Map<String, Object> claims, Jwt.Builder jwtBuilder) {
        claims.forEach((key, value) -> {
            if (isDateClaim(key) && value instanceof Number number) {
                jwtBuilder.claim(key, Instant.ofEpochSecond(number.longValue()));
            } else {
                jwtBuilder.claim(key, value);
            }
        });
    }

    private static boolean isDateClaim(String key) {
        return JwtClaimNames.IAT.equals(key) || JwtClaimNames.EXP.equals(key);
    }
}
