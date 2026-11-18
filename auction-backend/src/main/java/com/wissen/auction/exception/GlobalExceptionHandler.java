package com.wissen.auction.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler – converts runtime exceptions into clean JSON error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.dao.TransientDataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleTemporaryFailure(org.springframework.dao.TransientDataAccessException ex) {
        return ResponseEntity.status(503).header("Retry-After", "1")
                .body(Map.of("status", 503, "message", "The database is busy. Refresh and retry shortly."));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(org.springframework.dao.DataIntegrityViolationException ex) {
        return buildError(HttpStatus.CONFLICT, "The request conflicts with existing data. Refresh and retry.");
    }

    @ExceptionHandler(com.wissen.auction.auth.LoginThrottle.TooManyLoginAttempts.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(com.wissen.auction.auth.LoginThrottle.TooManyLoginAttempts ex) {
        return ResponseEntity.status(429).header("Retry-After", Long.toString(ex.retryAfter()))
                .body(Map.of("status", 429, "message", ex.getMessage()));
    }

    @ExceptionHandler(com.wissen.auction.auth.InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(com.wissen.auction.auth.InvalidCredentialsException ex) {
        return buildError(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurity(SecurityException ex) {
        return buildError(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        org.slf4j.LoggerFactory.getLogger(getClass()).error("Unhandled request failure", ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field   = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            errors.put(field, message);
        });

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Failed");
        body.put("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
