package com.lukksarna.skystarter.infrastructure.config;

import com.lukksarna.skystarter.domain.service.SkyValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public SkyValidator skyValidator() {
        return new SkyValidator();
    }
}
