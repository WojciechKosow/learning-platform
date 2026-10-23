package com.project.learning_platform.service;

import com.project.learning_platform.entity.User;
import com.project.learning_platform.security.AccessToken;


/** Result of register / login / refresh: the access token for the body, the refresh token for the cookie. */
public record AuthSession(User user, AccessToken accessToken, IssuedRefreshToken refreshToken) {
}
