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
        long iat = Instant.now().getEpochSecond();
        String token = buildOfflineToken(
                "{\"alg\":\"none\",\"typ\":\"JWT\"}",
                "{\"sub\":\"user\",\"iat\":" + iat + ",\"exp\":" + (iat + 60) + "}");

        Jwt jwt = KeycloakAuthenticationConverter.parseOfflineToken(token);

        assertThat(jwt.getSubject()).isEqualTo("user");
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isNotNull();
    }

    @Test
    void parseOfflineToken_preservesNestedClaimsAsIs() {
        String token = buildOfflineToken(
                "{\"alg\":\"none\"}",
                "{\"sub\":\"u\",\"realm_access\":{\"roles\":[\"ROLE_USER\"]}}");

        Jwt jwt = KeycloakAuthenticationConverter.parseOfflineToken(token);

        assertThat(jwt.<Map<String, Object>>getClaim("realm_access").get("roles"))
                .isEqualTo(List.of("ROLE_USER"));
    }

    @Test
    void converter_realmAccessRolesNotAList_returnsNoAuthorities() {
        JwtAuthenticationConverter converter = KeycloakAuthenticationConverter.getJwtAuthenticationConverter();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("u")
                .claim("realm_access", Map.of("roles", "ROLE_USER"))
                .build();

        AbstractAuthenticationToken auth = converter.convert(jwt);

        assertThat(auth.getAuthorities())
                .filteredOn(SimpleGrantedAuthority.class::isInstance)
                .isEmpty();
    }

    @Test
    void converter_dropsNonStringEntriesInRolesList() {
        JwtAuthenticationConverter converter = KeycloakAuthenticationConverter.getJwtAuthenticationConverter();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("u")
                .claim("realm_access", Map.of("roles", List.of("ROLE_USER", 42)))
                .build();

        AbstractAuthenticationToken auth = converter.convert(jwt);

        assertThat(auth.getAuthorities())
                .filteredOn(SimpleGrantedAuthority.class::isInstance)
                .extracting(Object::toString)
                .containsExactly("ROLE_USER");
    }

    private static String buildOfflineToken(String headerJson, String payloadJson) {
        java.util.Base64.Encoder enc = java.util.Base64.getUrlEncoder().withoutPadding();
        return enc.encodeToString(headerJson.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + "." + enc.encodeToString(payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + "." + enc.encodeToString("sig".getBytes());
    }
}
