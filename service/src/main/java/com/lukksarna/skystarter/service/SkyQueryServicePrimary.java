package com.lukksarna.skystarter.service;

import com.lukksarna.skystarter.domain.model.Sky;
import com.lukksarna.skystarter.domain.port.SkyQueryService;
import com.lukksarna.skystarter.domain.query.FindSkyByIdQuery;
import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class SkyQueryServicePrimary implements SkyQueryService {

    private final QueryGateway queryGateway;

    @Override
    public CompletableFuture<Sky> findById(UUID skyId) {
        return queryGateway
                .query(new FindSkyByIdQuery(skyId), Sky.class);
    }
}
