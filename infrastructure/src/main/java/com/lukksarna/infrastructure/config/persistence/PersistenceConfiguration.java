package com.lukksarna.infrastructure.config.persistence;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(
    basePackages = "com.lukksarna.infrastructure.persistence"
)
@EntityScan(
    basePackages = "com.lukksarna.infrastructure.persistence"
)
public class PersistenceConfiguration {
}
