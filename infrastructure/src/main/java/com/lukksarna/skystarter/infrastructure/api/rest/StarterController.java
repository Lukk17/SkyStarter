package com.lukksarna.skystarter.infrastructure.api.rest;

import com.lukksarna.skystarter.domain.port.SkyCommandService;
import com.lukksarna.skystarter.domain.port.SkyQueryService;
import com.lukksarna.skystarter.infrastructure.api.rest.dto.request.CreateSkyRequest;
import com.lukksarna.skystarter.infrastructure.api.rest.dto.request.UpdateSkyRequest;
import com.lukksarna.skystarter.infrastructure.api.rest.dto.response.CreateSkyResponse;
import com.lukksarna.skystarter.infrastructure.api.rest.dto.response.SkyResponse;
import com.lukksarna.skystarter.infrastructure.config.api.inbound.ApiCommonErrorResponses;
import com.lukksarna.skystarter.infrastructure.config.security.SkyUser;
import com.lukksarna.skystarter.infrastructure.mapper.SkyApiMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
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
            summary = "Get a Sky",
            description = "Returns the current state of the Sky with the given id."
    )
    @ApiResponse(responseCode = "200", description = "Sky found")
    @ApiCommonErrorResponses
    @GetMapping(value = "/{skyId}", version = "v1")
    public CompletableFuture<ResponseEntity<SkyResponse>> getSky(@PathVariable("skyId") UUID skyId) {
        return skyQueryService
                .findById(skyId)
                .thenApply(skyApiMapper::domainToApiResponse)
                .thenApply(ResponseEntity::ok);
    }

    @Operation(
            summary = "Create a Sky",
            description = "Creates a new Sky and returns its id with a Location header pointing at the created resource."
    )
    @ApiResponse(responseCode = "201", description = "Sky created")
    @ApiCommonErrorResponses
    @PostMapping(version = "v1")
    public CompletableFuture<ResponseEntity<CreateSkyResponse>> createSky(@Valid @RequestBody CreateSkyRequest request) {
        return skyCommandService.createSky(request.getName())
                .thenApply(id -> ResponseEntity
                        .created(URI.create("/v1/starter/" + id))
                        .body(new CreateSkyResponse(id)));
    }

    @Operation(
            summary = "Update a Sky",
            description = "Renames an existing Sky."
    )
    @ApiResponse(responseCode = "204", description = "Sky updated")
    @ApiCommonErrorResponses
    @PutMapping(value = "/{skyId}", version = "v1")
    public CompletableFuture<ResponseEntity<Void>> updateSky(@PathVariable("skyId") UUID skyId, @Valid @RequestBody UpdateSkyRequest request) {
        return skyCommandService.updateSky(skyId, request.getName())
                .thenApply(ignored -> ResponseEntity.noContent().build());
    }

    @Operation(
            summary = "Delete a Sky",
            description = "Deletes a Sky. Not idempotent: deleting an unknown id returns 404."
    )
    @ApiResponse(responseCode = "204", description = "Sky deleted")
    @ApiCommonErrorResponses
    @DeleteMapping(value = "/{skyId}", version = "v1")
    public CompletableFuture<ResponseEntity<Void>> deleteSky(@PathVariable("skyId") UUID skyId) {
        return skyCommandService.deleteSky(skyId)
                .thenApply(ignored -> ResponseEntity.noContent().build());
    }
}
