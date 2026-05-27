package com.lukksarna.skystarter.infrastructure.config;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ResourceBanner;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class StartupLogConfig {

    private static final Logger log = LoggerFactory.getLogger(StartupLogConfig.class);

    private static final String SEPARATOR = "----------------------------------------------------------";
    private static final String SECTION_INDENT = "    ";
    private static final String FIELD_INDENT = "      ";
    private static final String LINE = "\n";

    private static final String DEFAULT_APPLICATION_NAME = "application";
    private static final String DEFAULT_SERVER_PORT = "8080";
    private static final String DEFAULT_CONTEXT_PATH = "";
    private static final String DEFAULT_PROFILE_LABEL = "default";
    private static final String UNKNOWN_HOST = "unknown";
    private static final String NOT_CONFIGURED = "[not configured]";
    private static final String STATUS_OK = "[OK]";
    private static final String STATUS_FAILED = "[FAILED]";

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);
    private static final String BANNER_RESOURCE = "banner.txt";

    private final Environment environment;
    private final AtomicBoolean logged = new AtomicBoolean(false);

    public StartupLogConfig(Environment environment) {
        this.environment = environment;
    }

    @EventListener
    public void onAcceptingTraffic(AvailabilityChangeEvent<ReadinessState> event) {
        if (event.getState() != ReadinessState.ACCEPTING_TRAFFIC) {
            return;
        }
        if (!logged.compareAndSet(false, true)) {
            return;
        }
        log.info("{}", buildStartupBlock());
    }

    private String buildStartupBlock() {
        String applicationName = environment.getProperty("spring.application.name", DEFAULT_APPLICATION_NAME);
        String serverPort = environment.getProperty("server.port", DEFAULT_SERVER_PORT);
        String contextPath = environment.getProperty("server.servlet.context-path", DEFAULT_CONTEXT_PATH);
        String hostname = resolveHostName();
        String baseLocalUrl = "http://localhost:" + serverPort + contextPath;
        String baseHostUrl = "http://" + hostname + ":" + serverPort + contextPath;

        StringBuilder out = new StringBuilder();
        out.append(LINE);
        appendBanner(out);
        out.append(SEPARATOR).append(LINE);
        out.append(SECTION_INDENT).append("Application '").append(applicationName).append("' is running!").append(LINE);
        out.append(LINE);
        appendAccessUrls(out, baseLocalUrl, baseHostUrl);
        appendProfiles(out);
        appendAuth(out);
        appendEventStore(out);
        appendProjections(out);
        appendAxon(out);
        appendActuator(out, baseLocalUrl);
        appendApiDocs(out, baseLocalUrl);
        appendTracing(out);
        appendLogging(out);
        out.append(SEPARATOR);
        return out.toString();
    }

    private void appendBanner(StringBuilder out) {
        try {
            Resource bannerResource = new ClassPathResource(BANNER_RESOURCE);
            if (!bannerResource.exists()) {
                return;
            }
            ByteArrayOutputStream rendered = new ByteArrayOutputStream();
            try (PrintStream stream = new PrintStream(rendered, true, StandardCharsets.UTF_8)) {
                new ResourceBanner(bannerResource).printBanner(environment, StartupLogConfig.class, stream);
            }
            out.append(rendered.toString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.debug("Failed to render banner resource '{}': {}", BANNER_RESOURCE, e.getMessage());
        }
    }

    private void appendAccessUrls(StringBuilder out, String baseLocalUrl, String baseHostUrl) {
        out.append(SECTION_INDENT).append("Access URLs:").append(LINE);
        out.append(FIELD_INDENT).append("Local:     ").append(baseLocalUrl).append(LINE);
        out.append(FIELD_INDENT).append("Hostname:  ").append(baseHostUrl).append(LINE);
        out.append(LINE);
    }

    private void appendProfiles(StringBuilder out) {
        out.append(SECTION_INDENT).append("Profile(s): ").append(formatActiveProfiles()).append(LINE);
        out.append(LINE);
    }

    private void appendAuth(StringBuilder out) {
        String issuerUri = environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri", NOT_CONFIGURED);
        String jwkSetUri = environment.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", NOT_CONFIGURED);
        String jwkStatus = probe(jwkSetUri);

        out.append(SECTION_INDENT).append("Auth (OAuth2 Resource Server):").append(LINE);
        out.append(FIELD_INDENT).append("Issuer:   ").append(issuerUri).append(LINE);
        out.append(FIELD_INDENT).append("JWK Set:  ").append(jwkSetUri).append(' ').append(jwkStatus).append(LINE);
        out.append(LINE);
    }

    private void appendEventStore(StringBuilder out) {
        String url = environment.getProperty("spring.datasource.url", NOT_CONFIGURED);
        String dialect = environment.getProperty("spring.jpa.database-platform", NOT_CONFIGURED);
        String changelog = environment.getProperty("spring.liquibase.change-log", NOT_CONFIGURED);
        String ddlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto", NOT_CONFIGURED);

        out.append(SECTION_INDENT).append("Event Store (PostgreSQL):").append(LINE);
        out.append(FIELD_INDENT).append("URL:      ").append(url).append(LINE);
        out.append(FIELD_INDENT).append("Schema:   Liquibase (").append(changelog)
                .append("), Hibernate ddl-auto=").append(ddlAuto).append(LINE);
        out.append(FIELD_INDENT).append("Dialect:  ").append(shortClassName(dialect)).append(LINE);
        out.append(LINE);
    }

    private void appendProjections(StringBuilder out) {
        String uri = environment.getProperty("spring.data.mongodb.uri", NOT_CONFIGURED);
        String uuidRepresentation = environment.getProperty("spring.mongodb.representation.uuid", NOT_CONFIGURED);

        out.append(SECTION_INDENT).append("Projections (MongoDB):").append(LINE);
        out.append(FIELD_INDENT).append("URI:      ").append(uri).append(LINE);
        out.append(FIELD_INDENT).append("UUID:     ").append(uuidRepresentation).append(" (canonical 16-byte BSON)").append(LINE);
        out.append(LINE);
    }

    private void appendAxon(StringBuilder out) {
        String processorMode = environment.getProperty(
                "axon.eventhandling.processors.sky-projection-processor.mode", NOT_CONFIGURED);
        String segments = environment.getProperty(
                "axon.eventhandling.processors.sky-projection-processor.initial-segment-count", NOT_CONFIGURED);
        String eventsSerializer = environment.getProperty("axon.serializer.events", NOT_CONFIGURED);

        out.append(SECTION_INDENT).append("Axon Framework:").append(LINE);
        out.append(FIELD_INDENT).append("Processor:      sky-projection-processor (mode=").append(processorMode).append(")").append(LINE);
        out.append(FIELD_INDENT).append("Segments:       ").append(segments).append(LINE);
        out.append(FIELD_INDENT).append("Serializer:     ").append(eventsSerializer).append(LINE);
        out.append(LINE);
    }

    private void appendActuator(StringBuilder out, String baseLocalUrl) {
        String basePath = environment.getProperty("management.endpoints.web.base-path", "/actuator");
        out.append(SECTION_INDENT).append("Actuator:").append(LINE);
        out.append(FIELD_INDENT).append("Health:     ").append(baseLocalUrl).append(basePath).append("/health").append(LINE);
        out.append(FIELD_INDENT).append("Readiness:  ").append(baseLocalUrl).append(basePath).append("/health/readiness").append(LINE);
        out.append(FIELD_INDENT).append("Liveness:   ").append(baseLocalUrl).append(basePath).append("/health/liveness").append(LINE);
        out.append(LINE);
    }

    private void appendApiDocs(StringBuilder out, String baseLocalUrl) {
        String apiDocsPath = environment.getProperty("springdoc.api-docs.path", "/v3/api-docs");
        String swaggerPath = environment.getProperty("springdoc.swagger-ui.path", "/swagger-ui.html");
        out.append(SECTION_INDENT).append("API documentation:").append(LINE);
        out.append(FIELD_INDENT).append("OpenAPI:    ").append(baseLocalUrl).append(apiDocsPath).append(LINE);
        out.append(FIELD_INDENT).append("Swagger UI: ").append(baseLocalUrl).append(swaggerPath).append(LINE);
        out.append(LINE);
    }

    private void appendTracing(StringBuilder out) {
        String samplingProbability = environment.getProperty("management.tracing.sampling.probability", "1.0");
        String otlpEndpoint = environment.getProperty("management.otlp.tracing.endpoint", "");
        String otlpStatus = otlpEndpoint.isBlank()
                ? "[in-process only — set OTLP_TRACING_ENDPOINT to export]"
                : otlpEndpoint;

        out.append(SECTION_INDENT).append("Tracing:").append(LINE);
        out.append(FIELD_INDENT).append("Sampling: ").append(samplingProbability).append(LINE);
        out.append(FIELD_INDENT).append("OTLP:     ").append(otlpStatus).append(LINE);
        out.append(LINE);
    }

    private void appendLogging(StringBuilder out) {
        out.append(SECTION_INDENT).append("Logging:").append(LINE);
        out.append(FIELD_INDENT).append("Encoder:  ").append(describeLoggingEncoder()).append(LINE);
    }

    private String describeLoggingEncoder() {
        for (String profile : environment.getActiveProfiles()) {
            if ("docker".equals(profile) || "prod".equals(profile)) {
                return "JSON (LogstashEncoder) with traceId/spanId/jwt.subject MDC";
            }
        }
        return "text pattern with [traceId,spanId,jwt.subject] MDC";
    }

    private String formatActiveProfiles() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return DEFAULT_PROFILE_LABEL;
        }
        return String.join(", ", activeProfiles);
    }

    private static String probe(String url) {
        if (url == null || url.isBlank() || NOT_CONFIGURED.equals(url)) {
            return STATUS_FAILED;
        }
        try {
            timedRestClient().get().uri(url).retrieve().toBodilessEntity();
            return STATUS_OK;
        } catch (Exception e) {
            log.debug("Probe failed for {}: {}", url, e.getMessage());
            return STATUS_FAILED;
        }
    }

    private static RestClient timedRestClient() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllManagers(), new SecureRandom());
            HttpClient jdkClient = HttpClient.newBuilder()
                    .connectTimeout(PROBE_TIMEOUT)
                    .sslContext(sslContext)
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdkClient);
            factory.setReadTimeout(PROBE_TIMEOUT);
            return RestClient.builder().requestFactory(factory).build();
        } catch (GeneralSecurityException e) {
            return RestClient.builder().requestFactory(simpleTimedFactory()).build();
        }
    }

    private static SimpleClientHttpRequestFactory simpleTimedFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(PROBE_TIMEOUT);
        factory.setReadTimeout(PROBE_TIMEOUT);
        return factory;
    }

    private static TrustManager[] trustAllManagers() {
        return new TrustManager[] {
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
        };
    }

    private static String resolveHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return UNKNOWN_HOST;
        }
    }

    private static String shortClassName(String fullyQualifiedClassName) {
        if (fullyQualifiedClassName == null) {
            return NOT_CONFIGURED;
        }
        int lastDot = fullyQualifiedClassName.lastIndexOf('.');
        if (lastDot < 0) {
            return fullyQualifiedClassName;
        }
        return fullyQualifiedClassName.substring(lastDot + 1);
    }
}
