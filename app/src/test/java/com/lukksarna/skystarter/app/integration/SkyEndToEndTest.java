package com.lukksarna.skystarter.app.integration;

import com.lukksarna.skystarter.app.TestSecurityConfig;
import com.lukksarna.skystarter.app.TestcontainersConfiguration;
import com.lukksarna.skystarter.infrastructure.api.rest.dto.response.CreateSkyResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end CQRS round-trip + validation: PostgreSQL + MongoDB Testcontainers,
 * Liquibase, Axon 5 entity model, projection convergence (Awaitility).
 *
 * Spring filters are disabled (`addFilters = false`) so the test focuses on
 * application logic. Auth filtering is exercised by SecurityFilterTest.
 *
 * Spring 7 occasionally completes async controllers synchronously (already-
 * completed futures, exception paths). The {@link #performMaybeAsync} helper
 * dispatches only when async actually started, otherwise uses the result as-is.
 */
@Import({TestcontainersConfiguration.class, TestSecurityConfig.class})
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkyEndToEndTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    @Test
        void fullCqrsLifecycle_createUpdateGetDelete() throws Exception {
        MvcResult createOut = performMaybeAsync(post("/v1/starter")
                .with(user("u").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Andromeda\"}"));
        assertThat(createOut.getResponse().getStatus()).isEqualTo(201);

        UUID id = json.readValue(
                createOut.getResponse().getContentAsString(), CreateSkyResponse.class).skyId();

        await().atMost(15, SECONDS).untilAsserted(() -> {
            MvcResult got = performMaybeAsync(get("/v1/starter/" + id).with(user("u").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
            assertThat(got.getResponse().getStatus()).isEqualTo(200);
            assertThat(got.getResponse().getContentAsString()).contains("Andromeda");
        });

        MvcResult upd = performMaybeAsync(put("/v1/starter/" + id)
                .with(user("u").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Andromeda-2\"}"));
        assertThat(upd.getResponse().getStatus()).isEqualTo(204);

        await().atMost(15, SECONDS).untilAsserted(() -> {
            MvcResult got = performMaybeAsync(get("/v1/starter/" + id).with(user("u").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
            assertThat(got.getResponse().getStatus()).isEqualTo(200);
            assertThat(got.getResponse().getContentAsString()).contains("Andromeda-2");
        });

        MvcResult del = performMaybeAsync(delete("/v1/starter/" + id).with(user("u").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")))
                .with(csrf()));
        assertThat(del.getResponse().getStatus()).isEqualTo(204);

        await().atMost(15, SECONDS).untilAsserted(() -> {
            MvcResult got = performMaybeAsync(get("/v1/starter/" + id).with(user("u").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
            assertThat(got.getResponse().getStatus()).isEqualTo(404);
        });
    }

    @Test
        void createWithBlankName_returns400() throws Exception {
        mvc.perform(post("/v1/starter")
                        .with(user("u").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")))
                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:skystarter:error:validation"));
    }

    private MvcResult performMaybeAsync(RequestBuilder request) throws Exception {
        MvcResult initial = mvc.perform(request).andReturn();
        if (initial.getRequest().isAsyncStarted()) {
            return mvc.perform(asyncDispatch(initial)).andReturn();
        }
        return initial;
    }
}
