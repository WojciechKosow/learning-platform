package com.project.learning_platform.security;

import com.project.learning_platform.user.UserRole;

import java.security.Principal;
import java.util.UUID;

/**
 * The principal of an authenticated request. Inject it in controllers with
 * {@code @AuthenticationPrincipal AuthenticatedUser user}.
 */
public record AuthenticatedUser(UUID id, UserRole role) implements Principal {

    @Override
    public String getName() {
        return id.toString();
    }
}
