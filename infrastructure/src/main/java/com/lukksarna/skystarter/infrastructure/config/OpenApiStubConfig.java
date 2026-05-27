package com.lukksarna.skystarter.infrastructure.config;

import com.lukksarna.skystarter.domain.exception.SkyNotFoundException;
import com.lukksarna.skystarter.domain.model.Sky;
import com.lukksarna.skystarter.domain.port.SkyCommandService;
import com.lukksarna.skystarter.domain.port.SkyQueryService;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.inmemory.InMemoryTokenStore;
import org.springframework.core.env.Environment;
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
 * application-openapi.yaml excludes the JDBC/JPA/OAuth2 autoconfigs and turns
 * on spring.main.lazy-initialization. The @Primary stubs below win resolution
 * of SkyCommandService / SkyQueryService, so SkyServiceConfiguration's real
 * @Bean methods (which need the Axon gateways) are never called. Axon's pooled
 * event processor still starts and binds SkyProjection, so it needs a TokenStore
 * and an event store: the {@link #tokenStore()} bean below provides an in-memory
 * one, and with no EntityManagerFactory the JPA event store backs off and Axon
 * falls back to its default in-memory store. SkyProjection's MongoTemplate
 * connects lazily, so no MongoDB is contacted during a scrape.
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

    private static final Set<String> FORBIDDEN_COMPANION_PROFILES = Set.of("prod", "docker", "staging");

    /**
     * Fail fast if the permissive openapi profile is ever combined with a
     * prod-like profile. The profile disables auth and stubs out the write
     * path, so it must never run anywhere real -- it exists only for
     * `./gradlew generateOpenApiDocs`.
     */
    public OpenApiStubConfig(Environment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if (FORBIDDEN_COMPANION_PROFILES.contains(profile)) {
                throw new IllegalStateException(
                        "The 'openapi' profile is for spec generation only and must not run with the '"
                                + profile + "' profile -- it disables authentication and stubs the write path.");
            }
        }
    }

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

    // The pooled event processor needs a TokenStore; the JPA-backed one is
    // absent (no EntityManagerFactory in this profile). An in-memory store lets
    // the processor start without a database. Real (non-mock) Axon component.
    @Bean
    TokenStore tokenStore() {
        return new InMemoryTokenStore();
    }

    @Bean
    SecurityFilterChain openApiFilterChain(HttpSecurity http) {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .build();
    }
}
