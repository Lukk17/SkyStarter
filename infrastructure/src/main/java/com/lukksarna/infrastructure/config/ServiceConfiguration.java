package com.lukksarna.infrastructure.config;

import com.lukksarna.domain.port.StarterService;
import com.lukksarna.domain.service.StarterDomainService;
import com.lukksarna.service.StarterServicePrimary;
import com.lukksarna.service.port.StarterRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServiceConfiguration {

    @Bean
    public StarterDomainService starterDomainService() {
        return new StarterDomainService();
    }

    @Bean
    public StarterService starterService(StarterRepository starterRepository, StarterDomainService starterDomainService) {
        return new StarterServicePrimary(starterRepository, starterDomainService);
    }
}
