package com.project.learning_platform.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned in the {@code code} field of
 * every error response. The frontend should switch on these, never on the
 * human-readable {@code detail} text.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request could not be read."),
    EMAIL_ALREADY_USED(HttpStatus.CONFLICT, "An account with this email already exists."),
    USERNAME_ALREADY_USED(HttpStatus.CONFLICT, "This username is already taken."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password."),
    ACCOUNT_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "Too many failed login attempts. Try again later."),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "This account is disabled."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "The session is invalid or has expired. Please log in again."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found."),
    CONFLICT(HttpStatus.CONFLICT, "The request conflicts with the current state of the resource."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
