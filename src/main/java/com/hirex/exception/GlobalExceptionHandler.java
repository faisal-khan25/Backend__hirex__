package com.hirex.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * GlobalExceptionHandler  — FIXED
 *
 * FIXES APPLIED:
 *  FIX BUG-H3  : IllegalArgumentException and NoSuchElementException now map
 *                to 404 NOT FOUND (not 400). IllegalStateException maps to 409
 *                CONFLICT. This lets clients distinguish between bad input (400),
 *                not found (404), and conflict (409).
 *
 *  FIX BACK-1  : DataIntegrityViolationException (e.g. duplicate session for
 *                the same interview) returns 409 CONFLICT instead of 500.
 *
 *  FIX BUG-H4  : MethodArgumentNotValidException returns structured 400 with
 *                all field-level validation errors from @Valid.
 *
 *  CS-6        : All handlers now log the exception at the appropriate level
 *                so root causes are not silently swallowed.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── 403 Forbidden ────────────────────────────────────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied: " + ex.getMessage()));
    }

    // ── 404 Not Found ─────────────────────────────────────────────────────────
    /**
     * FIX BUG-H3: NoSuchElementException → 404 (was falling through to 400).
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * FIX BUG-H3: IllegalArgumentException → 404 for "not found" semantics.
     * (Kept for backward compatibility with any code still throwing it.)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    // ── 409 Conflict ──────────────────────────────────────────────────────────
    /**
     * FIX BUG-H3 + FIX BACK-1:
     *   IllegalStateException  → 409  (e.g. "session already active")
     *   DataIntegrityViolation → 409  (e.g. duplicate DB row on concurrent create)
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "A conflicting record already exists. Please check for duplicates."));
    }

    // ── 400 Bad Request ───────────────────────────────────────────────────────
    /**
     * FIX BUG-H4: Returns structured validation errors from @Valid / @Validated.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (a, b) -> a   // keep first error per field
                ));
        log.warn("Validation failed: {}", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Validation failed", "details", fieldErrors));
    }

    // ── 500 Internal Server Error ─────────────────────────────────────────────
    /**
     * CS-6 fix: log the full exception so root cause is not silently swallowed.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred. Please try again."));
    }
}