package com.project.learning_platform.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

/**
 * Turns every failure into an RFC 9457 {@code application/problem+json} body
 * with two extension members: {@code code} (an {@link ErrorCode} name the
 * frontend can switch on) and, for validation failures, {@code errors}.
 *
 * <p>Unexpected exceptions are logged with their stack trace and answered with
 * a generic 500 — internal messages never reach the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record FieldError(String field, String message) {
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        return problem(ex.getCode(), ex.getMessage());
    }

    /** Reached via the security entry point (missing, invalid or expired bearer token). */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        log.debug("Authentication failed: {}", ex.getMessage());
        String message = ex instanceof InvalidBearerTokenException
                ? "The access token is invalid or has expired."
                : ErrorCode.UNAUTHORIZED.defaultMessage();
        return problem(ErrorCode.UNAUTHORIZED, message);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return problem(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.info("Concurrent modification: {}", ex.getMessage());
        return problem(ErrorCode.CONFLICT, "The resource was modified concurrently. Please retry.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        // The message can contain SQL and column values — log it, never return it.
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(ErrorCode.CONFLICT, ErrorCode.CONFLICT.defaultMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldError> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> new FieldError(
                        error instanceof org.springframework.validation.FieldError fieldError
                                ? fieldError.getField()
                                : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList();

        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED.defaultMessage());
        body.setProperty("code", ErrorCode.VALIDATION_FAILED.name());
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Framework exceptions handled by the base class (malformed JSON, wrong
     * method, unsupported media type, unknown route, ...) get a {@code code} too,
     * so every error body has the same shape.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             @Nullable Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            Map<String, Object> properties = problem.getProperties();
            if (properties == null || !properties.containsKey("code")) {
                problem.setProperty("code", codeFor(statusCode));
            }
        }
        return response;
    }

    private static String codeFor(HttpStatusCode status) {
        if (status.value() == HttpStatus.BAD_REQUEST.value()) {
            return ErrorCode.MALFORMED_REQUEST.name();
        }
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return ErrorCode.NOT_FOUND.name();
        }
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.name() : ErrorCode.INTERNAL_ERROR.name();
    }

    private static ResponseEntity<ProblemDetail> problem(ErrorCode code, String message) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(code.status(), message);
        body.setProperty("code", code.name());
        return ResponseEntity.status(code.status()).body(body);
    }
}
