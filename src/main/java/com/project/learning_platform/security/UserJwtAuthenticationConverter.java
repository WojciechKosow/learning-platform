package com.project.learning_platform.security;

import com.project.learning_platform.user.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Turns a verified JWT into an authentication backed by the current database
 * state: a token for a deleted or disabled account stops working immediately,
 * and role changes apply without waiting for the token to expire.
 */
@Component
public class UserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    public UserJwtAuthenticationConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = parseUserId(jwt.getSubject());

        return userRepository.findById(userId)
                .filter(user -> user.isActive())
                .map(user -> {
                    AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getRole());
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                    return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
                })
                .orElseThrow(() -> new InvalidBearerTokenException("Unknown or inactive user"));
    }

    private static UUID parseUserId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("Invalid token subject");
        }
    }
}
