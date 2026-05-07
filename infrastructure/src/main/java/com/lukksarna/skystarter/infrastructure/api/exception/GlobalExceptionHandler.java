package com.lukksarna.skystarter.infrastructure.api.exception;

import com.lukksarna.skystarter.domain.exception.SkyNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.messaging.queryhandling.QueryExecutionException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleApiArgumentNotValidException(MethodArgumentNotValidException ex) {
        log.warn("API request body not valid: {}", ex.getMessage());

        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage));

        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", errors));
    }

    @ExceptionHandler(SkyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(SkyNotFoundException ex) {
        log.info("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(404).body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("API illegal argument: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException ex) {
        log.info("No resource at path: {}", ex.getMessage());
        return ResponseEntity.status(404)
                .body(new ErrorResponse("NOT_FOUND", "There is no resource under specified path."));
    }

    /**
     * Axon 5 wraps query-handler exceptions in {@link QueryExecutionException}.
     * Unwrap and route to the appropriate handler.
     */
    @ExceptionHandler(QueryExecutionException.class)
    public ResponseEntity<ErrorResponse> handleQueryExecutionException(QueryExecutionException ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        if (cause instanceof SkyNotFoundException notFound) {
            return handleNotFound(notFound);
        }
        if (cause instanceof IllegalArgumentException illegal) {
            return handleIllegalArgumentException(illegal);
        }
        log.error("Unexpected query-execution error", cause);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_ERROR", "Something went wrong"));
    }

    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<ErrorResponse> handleCompletion(CompletionException ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        if (cause instanceof SkyNotFoundException notFound) {
            return handleNotFound(notFound);
        }
        if (cause instanceof IllegalArgumentException illegal) {
            return handleIllegalArgumentException(illegal);
        }
        log.error("Unexpected async error", cause);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_ERROR", "Something went wrong"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_ERROR", "Something went wrong"));
    }
}
