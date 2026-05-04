package com.lukksarna.skystarter.infrastructure.api.rest;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
public class StarterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // We are using mockBean for the services to simulate different behaviors.

    @MockBean(name = "skyCommandService")
    private SkyCommandService skyCommandService;

    @MockBean(name = "skyQueryService")
    private SkyQueryService skyQueryService;
    
    // CreateSkyRequest test data
    private static final String CREATE_SKY_REQUEST_JSON = "{\"name\":\"Test Sky\"}";
    
    // UpdateSkyRequest test data
    private static final UUID UPDATE_SKY_ID = UUID.randomUUID();
    private static final String UPDATE_SKY_NAME = "Updated Name";
    private static final String UPDATE_SKY_REQUEST_JSON = "{\"name\":\"" + UPDATE_SKY_NAME + "\"}";

    @BeforeEach
    public void setUp() {
        // We can inject the controller if needed, but with MockMvc and Mockito we don't need to.
    }

    // Test for createSky success case

    @Test
    void testCreateSky_Success() throws Exception {
        UUID expectedId = UUID.randomUUID();
        
        when(skyCommandService.createSky(anyString())).thenReturn(CompletableFuture.completedFuture(expectedId));
        
        mockMvc.perform(post("/v1/starter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_SKY_REQUEST_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().string(objectMapper.writeValueAsString(expectedId)));
    }

    // Test for createSky with invalid name (validation error)

    @Test
    void testCreateSky_ValidationError() throws Exception {
        String json = "{\"name\":null}";

        doThrow(new IllegalArgumentException("Wrong request parameter.")).when(skyCommandService).createSky(anyString());
        
        mockMvc.perform(post("/v1/starter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorType").value(equalTo("API Illegal argument")))
                .andExpect(jsonPath("$.message").value(equalTo("Wrong request parameter.")));
    }

    // Test for createSky with database error (internal server)

    @Test
    void testCreateSky_InternalServerError() throws Exception {
        doThrow(new RuntimeException("Database error")).when(skyCommandService).createSky(anyString());
        
        mockMvc.perform(post("/v1/starter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_SKY_REQUEST_JSON))
                .andExpect(status().is500Internal())
                .andExpect(jsonPath("$.errorType").value(equalTo("INTERNAL_ERROR")))
                .andExpect(jsonPath("$.message").value(equalTo("Something went wrong")));
    }

    // Test for getSky success case

    @Test
    void testGetSky_Success() throws Exception {
        UUID skyId = UUID.randomUUID();
        
        SkyResponse response = new SkyResponse(skyId, "Test Sky", "CREATED");
        String json = objectMapper.writeValueAsString(response);
        
        when(skyQueryService.findById(any())).thenReturn(CompletableFuture.completedFuture(new Sky()));
        
        mockMvc.perform(get("/v1/starter/" + skyId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").exists());
    }

    // Test for getSky with not found (404)

    @Test
    void testGetSky_NotFound() throws Exception {
        UUID skyId = UUID.randomUUID();
        
        when(skyQueryService.findById(any())).thenReturn(CompletableFuture.failedFuture(new NoResourceFoundException("Sky not found")));
        
        mockMvc.perform(get("/v1/starter/" + skyId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorType").value(equalTo("API Illegal argument")))
                .andExpect(jsonPath("$.message").value(equalTo("Wrong request parameter.")));
    }

    // Test for updateSky success

    @Test
    void testUpdateSky_Success() throws Exception {
        when(skyCommandService.updateSky(any(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        
        mockMvc.perform(post("/v1/starter/" + UPDATE_SKY_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_SKY_REQUEST_JSON))
                .andExpect(status().isOk());
    }

    // Test for updateSky with validation error

    @Test
    void testUpdateSky_ValidationError() throws Exception {
        String json = "{\"name\":null}";

        doThrow(new IllegalArgumentException("Wrong request parameter.")).when(skyCommandService).updateSky(any(), anyString());
        
        mockMvc.perform(post("/v1/starter/" + UPDATE_SKY_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorType").value(equalTo("API Illegal argument")))
                .andExpect(jsonPath("$.message").value(equalTo("Wrong request parameter.")));
    }

    // Test for updateSky with internal server error

    @Test
    void testUpdateSky_InternalServerError() throws Exception {
        doThrow(new RuntimeException("Database error")).when(skyCommandService).updateSky(any(), anyString());
        
        mockMvc.perform(post("/v1/starter/" + UPDATE_SKY_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_SKY_REQUEST_JSON))
                .andExpect(status().is500Internal())
                .andExpect(jsonPath("$.errorType").value(equalTo("INTERNAL_ERROR")))
                .andExpect(jsonPath("$.message").value(equalTo("Something went wrong")));
    }

}
