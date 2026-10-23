package com.project.learning_platform.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Set;

@Validated
@ConfigurationProperties("app.security.auth")
public record AuthProperties(
        /* Lifetime of a session without "remember me" (the cookie is a browser-session cookie). */
        @DefaultValue("1d") @NotNull Duration refreshTokenTtl,
        /* Lifetime of a "remember me" session (persistent cookie). */
        @DefaultValue("30d") @NotNull Duration rememberMeRefreshTokenTtl,
        /* Failed password attempts before login is locked. */
        @DefaultValue("5") @Min(1) int maxFailedLogins,
        @DefaultValue("15m") @NotNull Duration loginLockDuration,
        @DefaultValue @Valid Cookie cookie
) {

    public record Cookie(
            @DefaultValue("lp_refresh_token") @NotBlank String name,
            /* Must match the browser-visible path of the auth endpoints. */
            @DefaultValue("/api/auth") @NotBlank String path,
            @DefaultValue("true") boolean secure,
            /* Lax works whenever frontend and API share a site (e.g. app.example.com + api.example.com). */
            @DefaultValue("Lax") String sameSite,
            /* Optional; leave empty for a host-only cookie. */
            String domain
    ) {

        private static final Set<String> SAME_SITE_VALUES = Set.of("Strict", "Lax", "None");

        public Cookie {
            if (!SAME_SITE_VALUES.contains(sameSite)) {
                throw new IllegalArgumentException("app.security.auth.cookie.same-site must be Strict, Lax or None");
            }
            if ("None".equals(sameSite) && !secure) {
                throw new IllegalArgumentException("SameSite=None cookies must be Secure");
            }
        }
    }
}
