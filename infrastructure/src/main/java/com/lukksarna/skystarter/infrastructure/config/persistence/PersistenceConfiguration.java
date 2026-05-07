package com.lukksarna.skystarter.infrastructure.config.persistence;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(
        basePackages = "com.lukksarna.skystarter.infrastructure.persistence.repository.mongo"
)
// This is required to resolve the "strict repository mode" conflict.
// By pointing this to an empty package, we prevent the JPA scanner
// (activated by Axon's dependencies) from finding and conflicting with our Mongo repositories.
@EnableJpaRepositories(
        basePackages = "com.lukksarna.skystarter.infrastructure.persistence.repository.jpa"
)
@EntityScan(
        basePackages = {
                "com.lukksarna.skystarter.infrastructure.persistence",
                "org.axonframework"
        }
)
public class PersistenceConfiguration {
}
