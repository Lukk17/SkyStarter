package com.lukksarna.skystarter.infrastructure.config.api.inbound;

import org.springframework.web.accept.ApiVersionParser;

/**
 * Treats the API version as an opaque string, no transformation. The default
 * {@code SemanticApiVersionParser} strips leading non-digits, so a URL segment
 * {@code v1} parses to version {@code 1.0.0} and springdoc renders the path as
 * {@code /1/starter}. Keeping {@code v1} verbatim makes the URL segment, the
 * {@code @GetMapping(version = "v1")} attribute, and the generated OpenAPI path
 * all agree on {@code /v1/...}.
 */
public class StringApiVersionParser implements ApiVersionParser<String> {

    @Override
    public String parseVersion(String version) {
        return version;
    }
}
