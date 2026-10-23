package com.project.learning_platform.security;

import com.project.learning_platform.config.properties.AuthProperties;
import com.project.learning_platform.service.IssuedRefreshToken;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Builds and reads the httpOnly refresh-token cookie so every endpoint uses identical attributes. */
@Component
public class AuthCookies {

    private final AuthProperties.Cookie settings;

    public AuthCookies(AuthProperties properties) {
        this.settings = properties.cookie();
    }

    public ResponseCookie create(IssuedRefreshToken token) {
        ResponseCookie.ResponseCookieBuilder builder = base(token.value());
        if (token.rememberMe()) {
            // Persistent cookie. Without "remember me" it is a browser-session
            // cookie; the server-side expiry applies either way.
            builder.maxAge(Duration.between(Instant.now(), token.expiresAt()));
        }
        return builder.build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, settings.name());
        return Optional.ofNullable(cookie)
                .map(Cookie::getValue)
                .filter(StringUtils::hasText);
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(settings.name(), value)
                .httpOnly(true)
                .secure(settings.secure())
                .sameSite(settings.sameSite())
                .path(settings.path());
        if (StringUtils.hasText(settings.domain())) {
            builder.domain(settings.domain());
        }
        return builder;
    }
}
