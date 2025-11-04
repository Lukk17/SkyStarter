package com.lukksarna.domain.service;

import com.lukksarna.domain.model.Starter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class StarterEnricher {

    public Starter enrich(Starter starter) {
        return applyBusinessEnrichments(starter);
    }

    private Starter applyBusinessEnrichments(Starter starter) {
        return starter;
    }
}
