package com.lukksarna.skystarter.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.mock.env.MockEnvironment;

class StartupLogConfigTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger startupLogger;

    @BeforeEach
    void attachListAppender() {
        startupLogger = (Logger) LoggerFactory.getLogger(StartupLogConfig.class);
        appender = new ListAppender<>();
        appender.start();
        startupLogger.addAppender(appender);
    }

    @AfterEach
    void detachListAppender() {
        startupLogger.detachAppender(appender);
    }

    @Test
    void onAcceptingTraffic_logsCompleteStartupBlock() {
        StartupLogConfig config = new StartupLogConfig(populatedEnvironment());

        config.onAcceptingTraffic(acceptingTrafficEvent(config));

        String combined = capturedMessages();
        assertThat(combined).contains("Application 'sky-starter' is running!");
        assertThat(combined).contains("Local:     http://localhost:7777");
        assertThat(combined).contains("Issuer:   https://example.test/realms/local");
        assertThat(combined).contains("URL:      jdbc:postgresql://localhost:5432/starter");
        assertThat(combined).contains("URI:      mongodb://localhost:27017/starter");
        assertThat(combined).contains("Processor:      sky-projection-processor (mode=pooled)");
        assertThat(combined).contains("Health:     http://localhost:7777/actuator/health");
        assertThat(combined).contains("Swagger UI: http://localhost:7777/openapi/swagger-ui.html");
        assertThat(combined).contains("Tracing:");
        assertThat(combined).contains("Sampling: 1.0");
        assertThat(combined).contains("Logging:");
        assertThat(combined).contains("text pattern with [traceId,spanId,jwt.subject] MDC");
    }

    @Test
    void onReadinessChange_nonAcceptingState_doesNothing() {
        StartupLogConfig config = new StartupLogConfig(populatedEnvironment());

        config.onAcceptingTraffic(new AvailabilityChangeEvent<>(config, ReadinessState.REFUSING_TRAFFIC));

        assertThat(appender.list).isEmpty();
    }

    @Test
    void onAcceptingTraffic_dockerProfile_reportsJsonEncoder() {
        MockEnvironment env = populatedEnvironment();
        env.setActiveProfiles("docker");
        StartupLogConfig config = new StartupLogConfig(env);

        config.onAcceptingTraffic(acceptingTrafficEvent(config));

        assertThat(capturedMessages()).contains("JSON (LogstashEncoder) with traceId/spanId/jwt.subject MDC");
    }

    private String capturedMessages() {
        StringBuilder out = new StringBuilder();
        for (ILoggingEvent event : appender.list) {
            out.append(event.getFormattedMessage()).append('\n');
        }
        return out.toString();
    }

    private static MockEnvironment populatedEnvironment() {
        return new MockEnvironment()
                .withProperty("spring.application.name", "sky-starter")
                .withProperty("server.port", "7777")
                .withProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                        "https://example.test/realms/local")
                .withProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", "")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/starter")
                .withProperty("spring.jpa.database-platform",
                        "com.lukksarna.skystarter.infrastructure.config.persistence.ByteaEnforcedPostgresSQLDialect")
                .withProperty("spring.liquibase.change-log", "classpath:db/changelog/db.changelog-master.yaml")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("spring.data.mongodb.uri", "mongodb://localhost:27017/starter")
                .withProperty("spring.mongodb.representation.uuid", "standard")
                .withProperty("axon.eventhandling.processors.sky-projection-processor.mode", "pooled")
                .withProperty("axon.eventhandling.processors.sky-projection-processor.initial-segment-count", "8")
                .withProperty("axon.snapshot.trigger.threshold", "5")
                .withProperty("axon.serializer.events", "jackson")
                .withProperty("management.endpoints.web.base-path", "/actuator")
                .withProperty("springdoc.swagger-ui.path", "/openapi/swagger-ui.html")
                .withProperty("springdoc.api-docs.path", "/openapi/v3/api-docs")
                .withProperty("management.tracing.sampling.probability", "1.0");
    }

    private static AvailabilityChangeEvent<ReadinessState> acceptingTrafficEvent(Object source) {
        return new AvailabilityChangeEvent<>(source, ReadinessState.ACCEPTING_TRAFFIC);
    }
}
