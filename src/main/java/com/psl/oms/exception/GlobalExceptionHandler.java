package com.psl.oms.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * GlobalExceptionHandler — centralised error handling for all REST controllers.
 *
 * Uses Spring 6's ProblemDetail (RFC 7807) for a consistent JSON error shape:
 * {
 *   "type":       "https://oms.psl.com/errors/not-found",
 *   "title":      "Resource Not Found",
 *   "status":     404,
 *   "detail":     "Order not found with id: '99'",
 *   "timestamp":  "2024-01-15T10:30:00Z"
 * }
 *
 * Phase 3 addition: handlers for CompletionException and ExecutionException —
 * exceptions that wrap errors thrown inside CompletableFuture chains.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String ERROR_BASE_URI = "https://oms.psl.com/errors/";

    // ── Phase 2 handlers (unchanged) ────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Resource Not Found");
        detail.setType(URI.create(ERROR_BASE_URI + "not-found"));
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicateResource(DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Duplicate Resource");
        detail.setType(URI.create(ERROR_BASE_URI + "conflict"));
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        detail.setTitle("Business Rule Violation");
        detail.setType(URI.create(ERROR_BASE_URI + "business-rule"));
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "Invalid value" : fe.getDefaultMessage(),
                        (existing, replacement) -> existing
                ));
        log.warn("Validation failed: {}", fieldErrors);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        detail.setTitle("Validation Error");
        detail.setType(URI.create(ERROR_BASE_URI + "validation"));
        detail.setProperty("timestamp", Instant.now());
        detail.setProperty("fieldErrors", fieldErrors);
        return detail;
    }

    // ── Phase 3 additions ────────────────────────────────────────────────

    /**
     * Handles CompletionException — the runtime exception that wraps checked and
     * unchecked exceptions thrown inside CompletableFuture.supplyAsync() / thenApply().
     *
     * When a CompletableFuture completes exceptionally and .join() or .get() is called,
     * the original exception is wrapped in CompletionException. Without this handler,
     * Spring would map it as a generic 500 — losing the 404/422 status of the real cause.
     *
     * This handler unwraps the cause and re-dispatches to the appropriate handler.
     *
     * Example: if generateBillAsync() causes a ResourceNotFoundException inside the future,
     * the client still gets a proper 404 response rather than a generic 500.
     */
    @ExceptionHandler(CompletionException.class)
    public ProblemDetail handleCompletionException(CompletionException ex) {
        log.warn("[ASYNC] CompletionException unwrapped: {}", ex.getMessage());
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        return mapCauseToProblemDetail(cause);
    }

    /**
     * Handles ExecutionException — the checked equivalent of CompletionException,
     * thrown when Future.get() is called and the task threw an exception.
     *
     * Rarely reaches controllers directly (CompletableFuture.join() throws
     * CompletionException, not ExecutionException), but included for completeness.
     */
    @ExceptionHandler(ExecutionException.class)
    public ProblemDetail handleExecutionException(ExecutionException ex) {
        log.warn("[ASYNC] ExecutionException unwrapped: {}", ex.getMessage());
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        return mapCauseToProblemDetail(cause);
    }

    /**
     * Catch-all for any exception not matched above — HTTP 500.
     * Logs the full stack trace but returns a generic message (never leak internals).
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unhandled exception: ", ex);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please contact support.");
        detail.setTitle("Internal Server Error");
        detail.setType(URI.create(ERROR_BASE_URI + "internal"));
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /**
     * Maps the unwrapped cause of an async exception to the correct ProblemDetail.
     * Mirrors the logic of the individual @ExceptionHandler methods so the HTTP status
     * is consistent whether the exception comes from a synchronous or async path.
     */
    private ProblemDetail mapCauseToProblemDetail(Throwable cause) {
        if (cause instanceof ResourceNotFoundException ex) {
            return handleResourceNotFound(ex);
        }
        if (cause instanceof BusinessRuleException ex) {
            return handleBusinessRule(ex);
        }
        if (cause instanceof DuplicateResourceException ex) {
            return handleDuplicateResource(ex);
        }
        // Unknown async failure — treat as 500
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Async operation failed: " + cause.getMessage());
        detail.setTitle("Async Processing Error");
        detail.setType(URI.create(ERROR_BASE_URI + "async-error"));
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }
}
