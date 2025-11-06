package com.lukksarna.skystarter.infrastructure.config.persistence;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(
        basePackages = "com.lukksarna.skystarter.infrastructure.persistence"
)
@EnableJpaRepositories(
        basePackages = "com.lukksarna.skystarter.infrastructure.persistence"
)
@EntityScan(
        basePackages = {
                "com.lukksarna.skystarter.infrastructure.persistence",
                "org.axonframework"
        }
)
public class PersistenceConfiguration {
}
