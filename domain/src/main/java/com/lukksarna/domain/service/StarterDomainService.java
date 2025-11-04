package com.lukksarna.domain.service;

import com.lukksarna.domain.model.Starter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StarterDomainService {

    private final StarterValidator starterValidator = new StarterValidator();
    private final StarterEnricher starterEnricher = new StarterEnricher();

    public Starter processOrder(Starter starter) {
        log.info("Processing order: {}", starter);
        // Domain business rules
        starterValidator.validate(starter);

        // Apply domain transformations
        return starterEnricher.enrich(starter);
    }
}
