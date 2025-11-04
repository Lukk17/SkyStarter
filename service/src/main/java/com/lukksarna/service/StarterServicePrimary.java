package com.lukksarna.service;

import com.lukksarna.domain.model.Starter;
import com.lukksarna.domain.port.StarterService;
import com.lukksarna.domain.service.StarterDomainService;
import com.lukksarna.service.port.StarterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class StarterServicePrimary implements StarterService {

    private final StarterRepository starterRepository;
    private final StarterDomainService starterDomainService;

    public Starter getStarter(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Order ID cannot be null or empty");
        }

        Starter starter = starterRepository.findById(id);

        return  starterDomainService.processOrder(starter);
    }
}
