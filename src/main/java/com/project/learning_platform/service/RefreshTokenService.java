package com.project.learning_platform.service;

import com.project.learning_platform.config.properties.AuthProperties;
import com.project.learning_platform.entity.RefreshToken;
import com.project.learning_platform.entity.User;
import com.project.learning_platform.exceptions.ApiException;
import com.project.learning_platform.exceptions.ErrorCode;
import com.project.learning_platform.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues, rotates and revokes refresh tokens.
 *
 * <p>{@code noRollbackFor = ApiException.class}: when reuse is detected we
 * revoke every session <em>and</em> reject the request — the revocation must be
 * committed even though the request fails.
 */
@Service
@Transactional(noRollbackFor = ApiException.class)
public class RefreshTokenService {

    private static final Logger audit = LoggerFactory.getLogger("security.audit");
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final AuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, AuthProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public IssuedRefreshToken issue(User user, boolean rememberMe) {
        Instant now = Instant.now();
        Duration ttl = rememberMe ? properties.rememberMeRefreshTokenTtl() : properties.refreshTokenTtl();
        return create(user, rememberMe, now, now.plus(ttl));
    }

    public record Rotation(User user, IssuedRefreshToken token) {
    }

    /** Validates {@code rawToken}, revokes it and issues its successor (same absolute expiry). */
    public Rotation rotate(String rawToken) {
        Instant now = Instant.now();
        RefreshToken current = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(RefreshTokenService::invalid);
        User user = current.getUser();

        if (current.isRevoked()) {
            // A rotated (or logged-out) token came back: it was probably stolen.
            revokeAll(user.getId(), now);
            audit.warn("event=REFRESH_TOKEN_REUSE userId={} tokenId={}", user.getId(), current.getId());
            throw invalid();
        }
        if (current.isExpired(now)) {
            throw invalid();
        }
        if (!user.isActive()) {
            revokeAll(user.getId(), now);
            throw invalid();
        }
        if (repository.revokeIfActive(current.getId(), now) == 0) {
            // Lost a race with another request presenting the same token.
            revokeAll(user.getId(), now);
            audit.warn("event=REFRESH_TOKEN_CONCURRENT_REUSE userId={} tokenId={}", user.getId(), current.getId());
            throw invalid();
        }

        return new Rotation(user, create(user, current.isRememberMe(), now, current.getExpiresAt()));
    }

    /** Revokes the session behind {@code rawToken}, if any. Unknown or already revoked tokens are ignored. */
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> repository.revokeIfActive(token.getId(), Instant.now()));
    }

    public void revokeAll(UUID userId, Instant now) {
        repository.revokeAllForUser(userId, now);
    }

    private IssuedRefreshToken create(User user, boolean rememberMe, Instant now, Instant expiresAt) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        repository.save(new RefreshToken(user, hash(value), rememberMe, now, expiresAt));
        return new IssuedRefreshToken(value, expiresAt, rememberMe);
    }

    /** Tokens carry 256 bits of entropy, so a fast hash is sufficient (no need for BCrypt). */
    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
    }
}
