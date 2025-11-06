package com.lukksarna.skystarter.domain.port;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SkyCommandService {

    CompletableFuture<UUID> createSky(String name);

    CompletableFuture<Void> updateSky(UUID skyId, String name);

    CompletableFuture<Void> deleteSky(UUID skyId);
}
