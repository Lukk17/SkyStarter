package com.lukksarna.skystarter.infrastructure.api.rest;

import tools.jackson.databind.ObjectMapper;
import com.lukksarna.skystarter.domain.exception.SkyNotFoundException;
import com.lukksarna.skystarter.domain.model.Sky;
import com.lukksarna.skystarter.domain.port.SkyCommandService;
import com.lukksarna.skystarter.domain.port.SkyQueryService;
import com.lukksarna.skystarter.infrastructure.api.exception.GlobalExceptionHandler;
import com.lukksarna.skystarter.infrastructure.api.rest.dto.response.SkyResponse;
import com.lukksarna.skystarter.infrastructure.config.api.inbound.StringApiVersionParser;
import com.lukksarna.skystarter.infrastructure.mapper.SkyApiMapper;
import org.axonframework.eventsourcing.eventstore.AppendEventsTransactionRejectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.accept.PathApiVersionResolver;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StarterControllerTest {

    @Mock
    private SkyCommandService commandService;
    @Mock
    private SkyQueryService queryService;
    @Mock
    private SkyApiMapper apiMapper;

    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        StarterController controller = new StarterController(commandService, queryService, apiMapper);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setApiVersionStrategy(pathSegmentZeroVersionStrategy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static ApiVersionStrategy pathSegmentZeroVersionStrategy() {
        return new DefaultApiVersionStrategy(
                List.of(new PathApiVersionResolver(0)),
                new StringApiVersionParser(),
                false,
                null,
                true,
                null,
                null);
    }

    @Test
    void createSky_success_returns201WithBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(commandService.createSky(eq("Orion")))
                .thenReturn(CompletableFuture.completedFuture(id));

        MvcResult async = mvc.perform(post("/v1/starter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Orion\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(async))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/starter/" + id))
                .andExpect(jsonPath("$.skyId").value(id.toString()));
    }

    @Test
    void createSky_concurrentConflict_returns409() throws Exception {
        when(commandService.createSky(anyString()))
                .thenReturn(CompletableFuture.failedFuture(
                        new AppendEventsTransactionRejectedException("conflict")));

        MvcResult async = mvc.perform(post("/v1/starter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Orion\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(async))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:skystarter:error:conflict"));
    }

    @Test
    void createSky_blankName_returns400Validation() throws Exception {
        mvc.perform(post("/v1/starter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:skystarter:error:validation"))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void getSky_success_returnsResponse() throws Exception {
        UUID id = UUID.randomUUID();
        Sky sky = new Sky(id, "Orion", "CREATED");
        SkyResponse response = new SkyResponse(id, "Orion", "CREATED");
        when(queryService.findById(id)).thenReturn(CompletableFuture.completedFuture(sky));
        when(apiMapper.domainToApiResponse(sky)).thenReturn(response);

        MvcResult async = mvc.perform(get("/v1/starter/" + id))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(async))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skyId").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Orion"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void getSky_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(queryService.findById(id))
                .thenReturn(CompletableFuture.failedFuture(new SkyNotFoundException(id)));

        MvcResult async = mvc.perform(get("/v1/starter/" + id))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(async))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:skystarter:error:not-found"));
    }

    @Test
    void updateSky_success() throws Exception {
        UUID id = UUID.randomUUID();
        when(commandService.updateSky(eq(id), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult async = mvc.perform(put("/v1/starter/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Orion-2\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(async)).andExpect(status().isNoContent());
    }

    @Test
    void deleteSky_success() throws Exception {
        UUID id = UUID.randomUUID();
        when(commandService.deleteSky(id))
                .thenReturn(CompletableFuture.completedFuture(null));

        MvcResult async = mvc.perform(delete("/v1/starter/" + id))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(async)).andExpect(status().isNoContent());
    }

    @Test
    void createSky_internalError_returns500() throws Exception {
        when(commandService.createSky(anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));

        MvcResult async = mvc.perform(post("/v1/starter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Orion\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(async))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("urn:skystarter:error:internal"));
    }

    private static org.springframework.test.web.servlet.RequestBuilder asyncDispatch(MvcResult result) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .asyncDispatch(result);
    }
}
