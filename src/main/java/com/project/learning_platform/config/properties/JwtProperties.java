package com.project.learning_platform.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Access-token settings. There is deliberately no default secret: the
 * application refuses to start until {@code JWT_SECRET} is provided (the
 * {@code dev} profile supplies a local-only value).
 */
@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("learning-platform") @NotBlank String issuer,
        @DefaultValue("15m") @NotNull Duration accessTokenTtl
) {

    /** HS256 needs a key of at least 256 bits. */
    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException(
                    "app.security.jwt.secret is not set. Provide a random JWT_SECRET of at least "
                            + MIN_SECRET_BYTES + " bytes (e.g. `openssl rand -base64 48`).");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "app.security.jwt.secret is too short; HS256 requires at least " + MIN_SECRET_BYTES + " bytes.");
        }
        if (accessTokenTtl != null && (accessTokenTtl.isZero() || accessTokenTtl.isNegative())) {
            throw new IllegalArgumentException("app.security.jwt.access-token-ttl must be positive.");
        }
    }

    public SecretKey signingKey() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** Never print the secret (records include every component by default). */
    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", accessTokenTtl=" + accessTokenTtl + ", secret=****]";
    }
}
