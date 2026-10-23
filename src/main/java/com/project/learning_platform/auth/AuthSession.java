package com.project.learning_platform.auth;

import com.project.learning_platform.security.AccessToken;
import com.project.learning_platform.user.User;

/** Result of register / login / refresh: the access token for the body, the refresh token for the cookie. */
public record AuthSession(User user, AccessToken accessToken, IssuedRefreshToken refreshToken) {
}
