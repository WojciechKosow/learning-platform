package com.project.learning_platform.user;

import java.time.Instant;
import java.util.UUID;

/** Public view of the account. Never add credentials or security counters here. */
public record UserResponse(
        UUID id,
        String email,
        String username,
        UserRole role,
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getUsername(), user.getRole(), user.getCreatedAt());
    }
}
