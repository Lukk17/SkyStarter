package com.lukksarna.skystarter.infrastructure.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtSubjectMdcFilterTest {

    private final JwtSubjectMdcFilter filter = new JwtSubjectMdcFilter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void jwtAuthentication_bindsSubjectToMdcForChain_thenRemoves() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("alice@example.com")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        AtomicReference<String> insideChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> insideChain.set(MDC.get(JwtSubjectMdcFilter.MDC_KEY));

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(insideChain.get()).isEqualTo("alice@example.com");
        assertThat(MDC.get(JwtSubjectMdcFilter.MDC_KEY)).isNull();
    }

    @Test
    void anonymousAuthentication_doesNotBindMdc() throws Exception {
        SecurityContextHolder.clearContext();

        AtomicReference<String> insideChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> insideChain.set(MDC.get(JwtSubjectMdcFilter.MDC_KEY));

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(insideChain.get()).isNull();
        assertThat(MDC.get(JwtSubjectMdcFilter.MDC_KEY)).isNull();
    }
}
