package com.project.learning_platform.service;

import java.time.Instant;

/** A freshly minted refresh token. {@code value} is the raw secret and only ever goes into the cookie. */
public record IssuedRefreshToken(String value, Instant expiresAt, boolean rememberMe) {

    @Override
    public String toString() {
        return "IssuedRefreshToken[expiresAt=" + expiresAt + ", rememberMe=" + rememberMe + "]";
    }
}
