package com.lukksarna.skystarter.infrastructure.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

// Intentionally NOT a @Component. Registered inside the Spring Security filter
// chain (after the bearer-token authentication filter) via http.addFilterAfter
// in the security configs, so SecurityContextHolder is already populated when
// resolveSubject() runs. A @Component would be auto-registered in the outer
// servlet filter chain ahead of Spring Security, where the context is still
// empty -- making jwt.subject always null.
public class JwtSubjectMdcFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "jwt.subject";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String subject = resolveSubject();
        boolean bound = subject != null;
        if (bound) {
            MDC.put(MDC_KEY, subject);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (bound) {
                MDC.remove(MDC_KEY);
            }
        }
    }

    private static String resolveSubject() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject();
        }
        return null;
    }
}
