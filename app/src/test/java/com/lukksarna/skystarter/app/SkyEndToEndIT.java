package com.lukksarna.skystarter.app;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestcontainersConfiguration.class, TestSecurityConfig.class})
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkyEndToEndIT {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    @Test
    @WithMockUser(roles = "USER")
    void fullCqrsLifecycle_createUpdateGetDelete() throws Exception {
        MvcResult create = mvc.perform(post("/v1/starter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Andromeda\"}"))
                .andReturn();
        MvcResult createOut = mvc.perform(asyncDispatch(create))
                .andExpect(status().isCreated())
                .andReturn();

        UUID id = UUID.fromString(json.readValue(createOut.getResponse().getContentAsString(), String.class));

        await().atMost(15, SECONDS).untilAsserted(() -> {
            MvcResult getStart = mvc.perform(get("/v1/starter/" + id)).andReturn();
            MvcResult getEnd = mvc.perform(asyncDispatch(getStart)).andReturn();
            assertThat(getEnd.getResponse().getStatus()).isEqualTo(200);
            assertThat(getEnd.getResponse().getContentAsString()).contains("Andromeda");
        });

        MvcResult upd = mvc.perform(put("/v1/starter/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Andromeda-2\"}"))
                .andReturn();
        mvc.perform(asyncDispatch(upd)).andExpect(status().isOk());

        await().atMost(15, SECONDS).untilAsserted(() -> {
            MvcResult getStart = mvc.perform(get("/v1/starter/" + id)).andReturn();
            mvc.perform(asyncDispatch(getStart))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Andromeda-2"));
        });

        MvcResult del = mvc.perform(delete("/v1/starter/" + id)).andReturn();
        mvc.perform(asyncDispatch(del)).andExpect(status().isOk());

        await().atMost(15, SECONDS).untilAsserted(() -> {
            MvcResult getStart = mvc.perform(get("/v1/starter/" + id)).andReturn();
            mvc.perform(asyncDispatch(getStart)).andExpect(status().isNotFound());
        });
    }

    @Test
    @WithMockUser(roles = "USER")
    void createWithBlankName_returns400() throws Exception {
        mvc.perform(post("/v1/starter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unauthenticated_isRejected() throws Exception {
        mvc.perform(get("/v1/starter/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
