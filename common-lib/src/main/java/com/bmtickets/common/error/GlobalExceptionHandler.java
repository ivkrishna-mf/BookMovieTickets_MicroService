package com.bmtickets.common.error;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Uniform RFC 7807 (application/problem+json) error responses for every service.
 * Resilience4j rejections are translated so clients can distinguish "slow down" (429) from "unavailable" (503).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ProblemDetail> tooMany(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(problem(HttpStatus.TOO_MANY_REQUESTS, ex.getCode(), ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> business(BusinessException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(problem(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getDetails()));
    }

    /** Resilience4j per-instance rate limiter rejected the call. */
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ProblemDetail> rateLimited(RequestNotPermitted ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(problem(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests, please retry shortly", Map.of()));
    }

    /** Bulkhead (concurrency throttle) is saturated: shed load instead of queueing indefinitely. */
    @ExceptionHandler(BulkheadFullException.class)
    public ResponseEntity<ProblemDetail> bulkhead(BulkheadFullException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "2")
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "THROTTLED", "Service is busy, please retry", Map.of()));
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ProblemDetail> circuitOpen(CallNotPermittedException ex) {
        log.warn("Circuit open: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                        "A downstream dependency is unavailable, please retry later", Map.of()));
    }

    /** Bean-validation failures: list every offending field. Other Spring MVC errors (404, 405, missing header...) use the base class. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Map<String, Object> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> errors.put(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", Map.of("fields", errors)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> forbidden(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return ResponseEntity.internalServerError()
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error", Map.of()));
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail, Map<String, Object> extra) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("code", code);
        extra.forEach(pd::setProperty);
        return pd;
    }
}
