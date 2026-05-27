package com.lukksarna.skystarter.infrastructure.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.json.JsonMapper;

class ProblemDetailAuthenticationEntryPointTest {

    private final ProblemDetailAuthenticationEntryPoint entryPoint =
            new ProblemDetailAuthenticationEntryPoint(JsonMapper.builder().build());

    @Test
    void commence_writes401ProblemJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/starter/abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("no token"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        String body = response.getContentAsString();
        assertThat(body).contains("\"type\":\"urn:skystarter:error:unauthorized\"");
        assertThat(body).contains("\"title\":\"Unauthorized\"");
        assertThat(body).contains("\"status\":401");
        assertThat(body).doesNotContain("no token");
    }
}
