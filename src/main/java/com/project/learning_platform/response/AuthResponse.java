package com.project.learning_platform.response;

import com.project.learning_platform.dto.UserDTO;
import com.project.learning_platform.service.AuthSession;


/**
 * Body of register / login / refresh. The refresh token is never part of the
 * body — it is only set as an httpOnly cookie.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserDTO user
) {

    public static AuthResponse from(AuthSession session) {
        return new AuthResponse(
                session.accessToken().value(),
                "Bearer",
                session.accessToken().ttl().toSeconds(),
                UserDTO.from(session.user()));
    }
}
