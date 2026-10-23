package com.project.learning_platform.config.properties;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {

    private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> new JwtProperties(null, "issuer", Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not set");
        assertThatThrownBy(() -> new JwtProperties("  ", "issuer", Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSecretShorterThan256Bits() {
        assertThatThrownBy(() -> new JwtProperties("too-short", "issuer", Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void rejectsNonPositiveTtl() {
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, "issuer", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringNeverContainsTheSecret() {
        JwtProperties properties = new JwtProperties(VALID_SECRET, "issuer", Duration.ofMinutes(15));
        assertThat(properties.toString()).doesNotContain(VALID_SECRET);
    }
}
