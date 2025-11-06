package com.lukksarna.skystarter.infrastructure.config.security;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class KeycloakAuthenticationConverter {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";

    private static final Gson GSON = new Gson();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

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
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList())
        );
        return converter;
    }

    public static Jwt parseOfflineToken(String token) {
        try {
            String[] parts = token.split("\\.");
            String headerPayload = new String(URL_DECODER.decode(parts[0]), StandardCharsets.UTF_8);
            String payload = new String(URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);

            Map<String, Object> headers = GSON.fromJson(headerPayload, MAP_TYPE);
            Map<String, Object> claims = GSON.fromJson(payload, MAP_TYPE);

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
            if (isDateClaim(key)) {
                if (value instanceof Double d) {
                    jwtBuilder.claim(key, Instant.ofEpochSecond(d.longValue()));
                } else {
                    jwtBuilder.claim(key, value);
                }
            } else {
                jwtBuilder.claim(key, value);
            }
        });
    }

    private static boolean isDateClaim(String key) {
        return JwtClaimNames.IAT.equals(key) || JwtClaimNames.EXP.equals(key);
    }
}
