package com.lukksarna.skystarter.infrastructure.config.api.inbound;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiVersionConfig implements WebMvcConfigurer {

    private static final int VERSION_PATH_SEGMENT_INDEX = 0;

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        // StringApiVersionParser keeps "v1" verbatim so the URL segment, the
        // @GetMapping(version = "v1") attribute, and the generated OpenAPI path
        // all stay on /v1 (the default SemanticApiVersionParser would strip the
        // "v" and render /1 in the spec).
        configurer.usePathSegment(VERSION_PATH_SEGMENT_INDEX)
                .setVersionParser(new StringApiVersionParser());
    }
}
