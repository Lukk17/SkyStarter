package com.lukksarna.skystarter.infrastructure.config;

import com.lukksarna.skystarter.domain.port.SkyCommandService;
import com.lukksarna.skystarter.domain.port.SkyQueryService;
import com.lukksarna.skystarter.service.SkyCommandServicePrimary;
import com.lukksarna.skystarter.service.SkyQueryServicePrimary;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SkyServiceConfiguration {

    @Bean
    public SkyCommandService skyCommandService(CommandGateway commandGateway) {
        return new SkyCommandServicePrimary(commandGateway);
    }

    @Bean
    public SkyQueryService skyQueryService(QueryGateway queryGateway) {
        return new SkyQueryServicePrimary(queryGateway);
    }
}
