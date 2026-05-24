package com.lukksarna.skystarter.infrastructure.api.exception;

import com.lukksarna.skystarter.domain.exception.SkyNotFoundException;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.messaging.queryhandling.QueryExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String GENERIC_INVALID_ARGUMENT = "Invalid argument.";
    private static final String GENERIC_INTERNAL_ERROR = "Something went wrong.";
    private static final String GENERIC_NOT_FOUND = "There is no resource under specified path.";
    private static final String GENERIC_BAD_JSON = "Malformed JSON request body.";
    private static final String VALIDATION_DETAIL = "Request validation failed.";
    private static final String FORBIDDEN_DETAIL = "Access denied.";

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        // Method-security (@PreAuthorize) throws AuthorizationDeniedException (a
        // subclass) during controller invocation, so it surfaces here in the
        // @ControllerAdvice rather than at the filter-chain AccessDeniedHandler.
        // Without this handler it would fall through to handleException -> 500.
        log.warn("Access denied: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, FORBIDDEN_DETAIL);
        detail.setType(ProblemTypes.ACCESS_DENIED);
        detail.setTitle("Forbidden");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(detail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleApiArgumentNotValidException(MethodArgumentNotValidException ex) {
        log.warn("API request body not valid: {}", ex.getMessage());

        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (a, b) -> a));

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, VALIDATION_DETAIL);
        detail.setType(ProblemTypes.VALIDATION);
        detail.setTitle("Validation Error");
        detail.setProperty("errors", fieldErrors);
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleBadJson(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body", ex);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, GENERIC_BAD_JSON);
        detail.setType(ProblemTypes.BAD_JSON);
        detail.setTitle("Bad Request");
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(SkyNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(SkyNotFoundException ex) {
        log.info("Resource not found: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setType(ProblemTypes.NOT_FOUND);
        detail.setTitle("Not Found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("API illegal argument", ex);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, GENERIC_INVALID_ARGUMENT);
        detail.setType(ProblemTypes.ILLEGAL_ARGUMENT);
        detail.setTitle("Bad Request");
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResourceFoundException(NoResourceFoundException ex) {
        log.info("No resource at path: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, GENERIC_NOT_FOUND);
        detail.setType(ProblemTypes.NOT_FOUND);
        detail.setTitle("Not Found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail);
    }

    @ExceptionHandler(QueryExecutionException.class)
    public ResponseEntity<ProblemDetail> handleQueryExecutionException(QueryExecutionException ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        if (cause instanceof SkyNotFoundException notFound) {
            return handleNotFound(notFound);
        }
        if (cause instanceof IllegalArgumentException illegal) {
            return handleIllegalArgumentException(illegal);
        }
        log.error("Unexpected query-execution error", cause);
        return internalServerError();
    }

    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<ProblemDetail> handleCompletion(CompletionException ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        if (cause instanceof SkyNotFoundException notFound) {
            return handleNotFound(notFound);
        }
        if (cause instanceof IllegalArgumentException illegal) {
            return handleIllegalArgumentException(illegal);
        }
        log.error("Unexpected async error", cause);
        return internalServerError();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleException(Exception ex) {
        log.error("Unexpected error", ex);
        return internalServerError();
    }

    private static ResponseEntity<ProblemDetail> internalServerError() {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_INTERNAL_ERROR);
        detail.setType(ProblemTypes.INTERNAL);
        detail.setTitle("Internal Server Error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(detail);
    }
}
