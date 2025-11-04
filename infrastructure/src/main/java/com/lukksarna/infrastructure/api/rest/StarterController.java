package com.lukksarna.infrastructure.api.rest;

import com.lukksarna.domain.model.Starter;
import com.lukksarna.domain.port.StarterService;
import com.lukksarna.infrastructure.api.rest.dto.StarterResponse;
import com.lukksarna.infrastructure.config.api.inbound.ApiCommonErrorResponses;
import com.lukksarna.infrastructure.config.api.inbound.ApiCommonSuccessResponses;
import com.lukksarna.infrastructure.mapper.StarterApiMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Starter", description = "Starter API")
@RequestMapping(value = "/v1/starter", produces = "application/json")
public class StarterController {

    private final StarterService starterService;
    private final StarterApiMapper starterApiMapper;

    @Operation(
            summary = "Starter endpoint",
            description = "Example of starter endpoint."
    )
    @ApiCommonSuccessResponses
    @ApiCommonErrorResponses
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<StarterResponse> getOrder(@PathVariable("id") Long id) {

        Starter starter = starterService.getStarter(id);

        StarterResponse response = starterApiMapper.domainToApiResponse(starter);

        return ResponseEntity.ok(response);
    }
}
