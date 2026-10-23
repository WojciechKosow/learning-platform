package com.project.learning_platform.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Browser origins allowed to call the API with credentials (the refresh-token
 * cookie). Exact origins only, e.g. {@code https://app.example.com} — no
 * wildcards and no trailing slash. Empty means no cross-origin access.
 */
@ConfigurationProperties("app.cors")
public record CorsProperties(@DefaultValue List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        for (String origin : allowedOrigins) {
            if (origin.contains("*")) {
                throw new IllegalArgumentException(
                        "app.cors.allowed-origins must list exact origins; wildcards are not allowed with credentials: " + origin);
            }
            if (origin.endsWith("/")) {
                throw new IllegalArgumentException(
                        "app.cors.allowed-origins entries must not end with '/': " + origin);
            }
        }
    }
}
