package com.lukksarna.skystarter.infrastructure.api.exception;

import com.lukksarna.skystarter.domain.exception.SkyNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFoundException_returns404() {
        UUID id = UUID.randomUUID();
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(new SkyNotFoundException(id));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().details().toString()).contains(id.toString());
    }

    @Test
    void illegalArgument_returns400() {
        ResponseEntity<ErrorResponse> response =
                handler.handleIllegalArgumentException(new IllegalArgumentException("bad"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().details()).isEqualTo("bad");
    }

    @Test
    void noResource_returns404() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNoResourceFoundException(new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/x", "/x"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void completion_unwrapsNotFound() {
        UUID id = UUID.randomUUID();
        CompletionException ex = new CompletionException(new SkyNotFoundException(id));

        ResponseEntity<ErrorResponse> response = handler.handleCompletion(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void completion_unwrapsIllegalArgument() {
        CompletionException ex = new CompletionException(new IllegalArgumentException("boom"));

        ResponseEntity<ErrorResponse> response = handler.handleCompletion(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void completion_unknownCause_returns500() {
        CompletionException ex = new CompletionException(new RuntimeException("kaboom"));

        ResponseEntity<ErrorResponse> response = handler.handleCompletion(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void uncaught_returns500() {
        ResponseEntity<ErrorResponse> response = handler.handleException(new RuntimeException("x"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
