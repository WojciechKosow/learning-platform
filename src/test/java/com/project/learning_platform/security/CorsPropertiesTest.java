package com.project.learning_platform.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsPropertiesTest {

    @Test
    void trimsAndDropsBlankEntries() {
        CorsProperties properties = new CorsProperties(List.of(" http://localhost:3000 ", ""));
        assertThat(properties.allowedOrigins()).containsExactly("http://localhost:3000");
    }

    @Test
    void rejectsWildcards() {
        assertThatThrownBy(() -> new CorsProperties(List.of("*")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTrailingSlash() {
        assertThatThrownBy(() -> new CorsProperties(List.of("https://app.example.com/")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
