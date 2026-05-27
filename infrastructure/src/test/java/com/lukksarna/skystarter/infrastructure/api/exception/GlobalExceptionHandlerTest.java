package com.lukksarna.skystarter.infrastructure.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.lukksarna.skystarter.domain.exception.SkyNotFoundException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.axonframework.eventsourcing.eventstore.AppendEventsTransactionRejectedException;
import org.axonframework.messaging.queryhandling.QueryExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static ProblemDetail bodyOf(ResponseEntity<ProblemDetail> response) {
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        return body;
    }

    @Test
    void notFoundException_returns404ProblemDetail() {
        UUID id = UUID.randomUUID();
        ResponseEntity<ProblemDetail> response = handler.handleNotFound(new SkyNotFoundException(id));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(bodyOf(response).getType()).isEqualTo(ProblemTypes.NOT_FOUND);
        assertThat(bodyOf(response).getTitle()).isEqualTo("Not Found");
        assertThat(bodyOf(response).getDetail()).contains(id.toString());
    }

    @Test
    void illegalArgument_returns400AndDoesNotEchoRawMessage() {
        ResponseEntity<ProblemDetail> response =
                handler.handleIllegalArgumentException(new IllegalArgumentException("internal raw detail leak"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyOf(response).getType()).isEqualTo(ProblemTypes.ILLEGAL_ARGUMENT);
        assertThat(bodyOf(response).getDetail()).isEqualTo("Invalid argument.");
        assertThat(bodyOf(response).getDetail()).doesNotContain("internal raw detail leak");
    }

    @Test
    void noResource_returns404ProblemDetail() {
        ResponseEntity<ProblemDetail> response =
                handler.handleNoResourceFoundException(new NoResourceFoundException(HttpMethod.GET, "/x", "/x"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(bodyOf(response).getType()).isEqualTo(ProblemTypes.NOT_FOUND);
    }

    @Test
    void completion_unwrapsNotFound() {
        UUID id = UUID.randomUUID();
        CompletionException ex = new CompletionException(new SkyNotFoundException(id));

        ResponseEntity<ProblemDetail> response = handler.handleCompletion(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(bodyOf(response).getType()).isEqualTo(ProblemTypes.NOT_FOUND);
    }

    @Test
    void completion_unwrapsIllegalArgument() {
        CompletionException ex = new CompletionException(new IllegalArgumentException("boom"));

        ResponseEntity<ProblemDetail> response = handler.handleCompletion(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyOf(response).getType()).isEqualTo(ProblemTypes.ILLEGAL_ARGUMENT);
    }

    @Test
    void completion_unknownCause_returns500() {
        CompletionException ex = new CompletionException(new RuntimeException("kaboom"));

        ResponseEntity<ProblemDetail> response = handler.handleCompletion(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(bodyOf(response).getType()).isEqualTo(ProblemTypes.INTERNAL);
    }

    @Test
    void uncaught_returns500() {
        ResponseEntity<ProblemDetail> response = handler.handleException(new RuntimeException("x"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(bodyOf(response).getType()).isEqualTo(ProblemTypes.INTERNAL);
    }

    @Test
    void accessDenied_returns403ProblemDetail() {
        ResponseEntity<ProblemDetail> response =
                handler.handleAccessDenied(new AccessDeniedException("nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(bodyOf(response).getType()).isEqualTo(ProblemTypes.ACCESS_DENIED);
        assertThat(bodyOf(response).getTitle()).isEqualTo("Forbidden");
        assertThat(bodyOf(response).getDetail()).doesNotContain("nope");
    }

    @Test
    void conflict_returns409ProblemDetail() {
        ResponseEntity<ProblemDetail> response =
                handler.handleConflict(new AppendEventsTransactionRejectedException("conflicting append"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(bodyOf(response).getType()).isEqualTo(ProblemTypes.CONFLICT);
        assertThat(bodyOf(response).getTitle()).isEqualTo("Conflict");
        assertThat(bodyOf(response).getDetail()).doesNotContain("conflicting append");
    }

    @Test
    void completion_unwrapsConflict() {
        CompletionException ex =
                new CompletionException(new AppendEventsTransactionRejectedException("conflict"));

        ResponseEntity<ProblemDetail> response = handler.handleCompletion(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(bodyOf(response).getType()).isEqualTo(ProblemTypes.CONFLICT);
    }

    @Test
    void badJson_returns400() {
        ResponseEntity<ProblemDetail> response =
                handler.handleBadJson(new HttpMessageNotReadableException("malformed", null, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyOf(response).getType()).isEqualTo(ProblemTypes.BAD_JSON);
        assertThat(bodyOf(response).getDetail()).isEqualTo("Malformed JSON request body.");
    }

    @Test
    void queryExecution_unwrapsNotFound() {
        UUID id = UUID.randomUUID();
        QueryExecutionException ex = new QueryExecutionException("wrapped", new SkyNotFoundException(id));

        ResponseEntity<ProblemDetail> response = handler.handleQueryExecutionException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(bodyOf(response).getType()).isEqualTo(ProblemTypes.NOT_FOUND);
    }

    @Test
    void queryExecution_unwrapsIllegalArgument() {
        QueryExecutionException ex = new QueryExecutionException("wrapped", new IllegalArgumentException("boom"));

        ResponseEntity<ProblemDetail> response = handler.handleQueryExecutionException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyOf(response).getType()).isEqualTo(ProblemTypes.ILLEGAL_ARGUMENT);
    }

    @Test
    void queryExecution_unknownCause_returns500() {
        QueryExecutionException ex = new QueryExecutionException("wrapped", new RuntimeException("kaboom"));

        ResponseEntity<ProblemDetail> response = handler.handleQueryExecutionException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(bodyOf(response).getType()).isEqualTo(ProblemTypes.INTERNAL);
    }

    @Test
    void queryExecution_nullCause_returns500() {
        QueryExecutionException ex = new QueryExecutionException("no cause", null);

        ResponseEntity<ProblemDetail> response = handler.handleQueryExecutionException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(bodyOf(response).getType()).isEqualTo(ProblemTypes.INTERNAL);
    }

    @Test
    void completion_nullCause_returns500() {
        CompletionException ex = new CompletionException("no cause", null);

        ResponseEntity<ProblemDetail> response = handler.handleCompletion(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(bodyOf(response).getType()).isEqualTo(ProblemTypes.INTERNAL);
    }

    @Test
    void validation_nullFieldMessage_fallsBackToGenericMessage() throws Exception {
        MethodParameter parameter =
                new MethodParameter(Holder.class.getDeclaredMethod("handle", String.class), 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", null));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ProblemDetail> response = handler.handleApiArgumentNotValidException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) bodyOf(response).getProperties().get("errors");
        assertThat(errors).containsEntry("name", "invalid value");
    }

    @SuppressWarnings("unused")
    private static final class Holder {
        void handle(String name) {
        }
    }
}
