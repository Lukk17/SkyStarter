package com.lukksarna.skystarter.infrastructure.config.api.inbound;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiVersionConfig implements WebMvcConfigurer {

    private static final int VERSION_PATH_SEGMENT_INDEX = 0;

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.usePathSegment(VERSION_PATH_SEGMENT_INDEX);
    }
}
