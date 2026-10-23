package com.project.learning_platform.dto;

import com.project.learning_platform.entity.User;
import com.project.learning_platform.entity.UserRole;
import java.time.Instant;
import java.util.UUID;

/** Public view of the account. Never add credentials or security counters here. */
public record UserDTO(
        UUID id,
        String email,
        String username,
        UserRole role,
        Instant createdAt
) {

    public static UserDTO from(User user) {
        return new UserDTO(user.getId(), user.getEmail(), user.getUsername(), user.getRole(), user.getCreatedAt());
    }
}
