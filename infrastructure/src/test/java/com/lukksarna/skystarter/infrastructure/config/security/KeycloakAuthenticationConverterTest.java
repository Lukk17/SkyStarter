package com.lukksarna.skystarter.infrastructure.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeycloakAuthenticationConverterTest {

    @Test
    void converterMapsRealmRolesToAuthorities() {
        JwtAuthenticationConverter converter = KeycloakAuthenticationConverter.getJwtAuthenticationConverter();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user")
                .claim("realm_access", Map.of("roles", List.of("ROLE_USER", "ROLE_ADMIN")))
                .build();

        AbstractAuthenticationToken auth = converter.convert(jwt);

        assertThat(auth.getAuthorities())
                .filteredOn(SimpleGrantedAuthority.class::isInstance)
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void converterReturnsEmptyAuthoritiesWhenRealmAccessMissing() {
        JwtAuthenticationConverter converter = KeycloakAuthenticationConverter.getJwtAuthenticationConverter();

        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user").build();

        AbstractAuthenticationToken auth = converter.convert(jwt);

        assertThat(auth.getAuthorities())
                .filteredOn(SimpleGrantedAuthority.class::isInstance)
                .isEmpty();
    }

    @Test
    void parseOfflineToken_invalidTokenThrows() {
        assertThatThrownBy(() -> KeycloakAuthenticationConverter.parseOfflineToken("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parseOfflineToken_decodesHeaderAndClaims() {
        // header: {"alg":"none","typ":"JWT"}
        String header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long iat = Instant.now().getEpochSecond();
        String payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"user\",\"iat\":" + iat + ",\"exp\":" + (iat + 60) + "}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String sig = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("sig".getBytes());
        String token = header + "." + payload + "." + sig;

        Jwt jwt = KeycloakAuthenticationConverter.parseOfflineToken(token);

        assertThat(jwt.getSubject()).isEqualTo("user");
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isNotNull();
    }
}
