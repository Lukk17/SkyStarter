package com.lukksarna.skystarter.infrastructure.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ProblemDetailAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProblemDetailAccessDeniedHandler handler = new ProblemDetailAccessDeniedHandler(objectMapper);

    @Test
    void handle_writesProblemDetailJsonWithForbiddenStatus() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/starter/abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("nope"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("type").asString()).isEqualTo("urn:skystarter:error:access-denied");
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("title").asString()).isEqualTo("Forbidden");
        assertThat(body.get("detail").asString()).isEqualTo("Access denied.");
    }
}
