package com.lukksarna.skystarter.infrastructure.api.rest;

import com.lukksarna.skystarter.domain.port.SkyCommandService;
import com.lukksarna.skystarter.domain.port.SkyQueryService;
import com.lukksarna.skystarter.infrastructure.api.rest.dto.request.CreateSkyRequest;
import com.lukksarna.skystarter.infrastructure.api.rest.dto.request.UpdateSkyRequest;
import com.lukksarna.skystarter.infrastructure.api.rest.dto.response.SkyResponse;
import com.lukksarna.skystarter.infrastructure.config.api.inbound.ApiCommonErrorResponses;
import com.lukksarna.skystarter.infrastructure.config.api.inbound.ApiCommonSuccessResponses;
import com.lukksarna.skystarter.infrastructure.config.security.SkyUser;
import com.lukksarna.skystarter.infrastructure.mapper.SkyApiMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Starter", description = "Starter API")
@RequestMapping(value = "/{version}/starter", produces = "application/json")
@SkyUser
public class StarterController {

    private final SkyCommandService skyCommandService;
    private final SkyQueryService skyQueryService;
    private final SkyApiMapper skyApiMapper;

    @Operation(
            summary = "Starter endpoint",
            description = "Example of starter get endpoint."
    )
    @ApiCommonSuccessResponses
    @ApiCommonErrorResponses
    @GetMapping(value = "/{skyId}", version = "1")
    public CompletableFuture<ResponseEntity<SkyResponse>> getSky(@PathVariable("skyId") UUID skyId) {
        return skyQueryService
                .findById(skyId)
                .thenApply(skyApiMapper::domainToApiResponse)
                .thenApply(ResponseEntity::ok);
    }

    @Operation(
            summary = "Starter endpoint",
            description = "Example of starter create endpoint."
    )
    @ApiCommonSuccessResponses
    @ApiCommonErrorResponses
    @PostMapping(version = "1")
    public CompletableFuture<ResponseEntity<UUID>> createSky(@Valid @RequestBody CreateSkyRequest request) {
        return skyCommandService.createSky(request.getName())
                .thenApply(id -> ResponseEntity.status(HttpStatus.CREATED).body(id));
    }

    @Operation(
            summary = "Starter endpoint",
            description = "Example of starter update endpoint."
    )
    @ApiCommonSuccessResponses
    @ApiCommonErrorResponses
    @PutMapping(value = "/{skyId}", version = "1")
    public CompletableFuture<Void> updateSky(@PathVariable("skyId") UUID skyId, @Valid @RequestBody UpdateSkyRequest request) {
        return skyCommandService.updateSky(skyId, request.getName());
    }

    @Operation(
            summary = "Starter endpoint",
            description = "Example of starter delete endpoint."
    )
    @ApiCommonSuccessResponses
    @ApiCommonErrorResponses
    @DeleteMapping(value = "/{skyId}", version = "1")
    public CompletableFuture<Void> deleteSky(@PathVariable("skyId") UUID skyId) {
        return skyCommandService.deleteSky(skyId);
    }
}
