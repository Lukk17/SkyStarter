package com.lukksarna.skystarter.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Companion test to {@link SkyEndToEndIT} — keeps the security filter chain
 * active so unauthenticated access is rejected. Spring Security 7 returns 403
 * (Forbidden) where Spring Security 6 returned 401 for resource-server-style
 * unauthenticated GETs without an entry point configured; the test accepts
 * any 4xx client error to stay stable across upgrades.
 */
@Import({TestcontainersConfiguration.class, TestSecurityConfig.class})
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityFilterIT {

    @Autowired
    private MockMvc mvc;

    @Test
    void unauthenticatedRequest_isRejected() throws Exception {
        mvc.perform(get("/v1/starter/" + UUID.randomUUID()))
                .andExpect(status().is4xxClientError());
    }
}
