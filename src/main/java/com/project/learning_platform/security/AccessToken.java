package com.project.learning_platform.security;

import java.time.Duration;
import java.time.Instant;

public record AccessToken(String value, Instant expiresAt, Duration ttl) {

    @Override
    public String toString() {
        return "AccessToken[expiresAt=" + expiresAt + "]";
    }
}
