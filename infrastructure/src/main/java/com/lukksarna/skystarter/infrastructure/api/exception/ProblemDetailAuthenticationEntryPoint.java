package com.lukksarna.skystarter.infrastructure.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Emits an RFC 9457 {@code application/problem+json} body for 401s, mirroring
 * {@link ProblemDetailAccessDeniedHandler}'s 403 shape. Spring Security's
 * default {@code BearerTokenAuthenticationEntryPoint} returns a 401 with only
 * a {@code WWW-Authenticate} header and an empty body; this gives callers the
 * same problem-detail contract the OpenAPI spec advertises for 401.
 */
@Slf4j
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String UNAUTHORIZED_DETAIL = "Authentication required.";

    private final ObjectMapper objectMapper;

    public ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        log.warn("Unauthenticated request to {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, UNAUTHORIZED_DETAIL);
        detail.setType(ProblemTypes.UNAUTHORIZED);
        detail.setTitle("Unauthorized");

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), detail);
    }
}
