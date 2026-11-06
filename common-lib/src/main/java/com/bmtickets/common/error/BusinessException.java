package com.bmtickets.common.error;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** Domain error carrying the HTTP status it maps to and an optional machine-readable detail map. */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    public BusinessException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public BusinessException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? Map.of() : details;
    }

    public static BusinessException notFound(String what, Object id) {
        return new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " " + id + " not found");
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
