package com.lukksarna.skystarter.infrastructure.config;

import com.lukksarna.skystarter.domain.exception.SkyNotFoundException;
import com.lukksarna.skystarter.domain.model.Sky;
import com.lukksarna.skystarter.domain.port.SkyCommandService;
import com.lukksarna.skystarter.domain.port.SkyQueryService;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Everything the `openapi` Spring profile needs, in one place.
 *
 * The profile activates only when the springdoc-openapi Gradle plugin
 * runs `customBootRun --spring.profiles.active=openapi`. Spec generation
 * needs the controllers wired so springdoc can serialise their
 * @RestController annotations -- it does NOT need real PostgreSQL,
 * MongoDB, Liquibase, Keycloak, or Axon at runtime.
 *
 * application-openapi.yaml takes care of excluding the heavy autoconfigs
 * and turns on spring.main.lazy-initialization. With lazy init,
 * SkyServiceConfiguration's @Bean methods are never called (no one asks
 * for the real SkyCommandService / SkyQueryService) because the @Primary
 * stubs below win the resolution; SkyProjection is never instantiated
 * because nothing depends on it during a /openapi/v3/api-docs.yaml hit.
 *
 * The {@link #openApiFilterChain(HttpSecurity)} bean replaces the
 * production SecurityConfig (which is profile-gated to exclude openapi)
 * and lets the springdoc plugin scrape the spec without an Authorization
 * header.
 */
@Configuration
@Profile("openapi")
@EnableWebSecurity
@EnableMethodSecurity
public class OpenApiStubConfig {

    @Bean
    @Primary
    public SkyCommandService stubSkyCommandService() {
        return new SkyCommandService() {
            @Override
            public CompletableFuture<UUID> createSky(String name) {
                return CompletableFuture.completedFuture(UUID.randomUUID());
            }

            @Override
            public CompletableFuture<Void> updateSky(UUID skyId, String name) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> deleteSky(UUID skyId) {
                return CompletableFuture.completedFuture(null);
            }
        };
    }

    @Bean
    @Primary
    public SkyQueryService stubSkyQueryService() {
        return new SkyQueryService() {
            @Override
            public CompletableFuture<Sky> findById(UUID skyId) {
                return CompletableFuture.failedFuture(new SkyNotFoundException(skyId));
            }
        };
    }

    @Bean
    SecurityFilterChain openApiFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .build();
    }
}
