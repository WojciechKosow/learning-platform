package com.project.learning_platform.exceptions;

/**
 * An expected, client-facing failure. The message is returned to the client
 * as-is, so it must never contain internal details.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
