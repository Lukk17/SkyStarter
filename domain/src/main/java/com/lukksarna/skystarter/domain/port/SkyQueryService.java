package com.lukksarna.skystarter.domain.port;

import com.lukksarna.skystarter.domain.model.Sky;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SkyQueryService {
    CompletableFuture<Sky> findById(UUID skyId);
}