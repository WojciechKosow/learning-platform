package com.project.learning_platform.auth.dto;

import com.project.learning_platform.auth.AuthSession;
import com.project.learning_platform.user.UserResponse;

/**
 * Body of register / login / refresh. The refresh token is never part of the
 * body — it is only set as an httpOnly cookie.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {

    public static AuthResponse from(AuthSession session) {
        return new AuthResponse(
                session.accessToken().value(),
                "Bearer",
                session.accessToken().ttl().toSeconds(),
                UserResponse.from(session.user()));
    }
}
