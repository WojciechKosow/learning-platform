package com.project.learning_platform.service;

import com.project.learning_platform.config.properties.AuthProperties;
import com.project.learning_platform.dto.UserDTO;
import com.project.learning_platform.entity.User;
import com.project.learning_platform.exceptions.ApiException;
import com.project.learning_platform.exceptions.ErrorCode;
import com.project.learning_platform.repository.UserRepository;
import com.project.learning_platform.request.LoginRequest;
import com.project.learning_platform.request.RegisterRequest;
import com.project.learning_platform.security.JwtProvider;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger audit = LoggerFactory.getLogger("security.audit");

    /** BCrypt ignores input beyond 72 bytes and Spring's encoder rejects it. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthProperties properties;

    /** Compared against when the email is unknown, so response time does not reveal whether an account exists. */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider,
                       RefreshTokenService refreshTokenService,
                       AuthProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
        this.properties = properties;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthSession register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String username = request.username();

        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_USED);
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ApiException(ErrorCode.USERNAME_ALREADY_USED);
        }

        User user = new User(email, username, passwordEncoder.encode(request.password()));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent registration for the same email/username.
            throw duplicateFrom(e);
        }

        audit.info("event=USER_REGISTERED userId={}", user.getId());
        return startSession(user, false);
    }

    /**
     * {@code noRollbackFor}: a failed attempt must persist its counter (and the
     * lock) even though the request ends with an error.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthSession login(LoginRequest request) {
        Instant now = Instant.now();
        Optional<User> found = userRepository.findByEmail(normalizeEmail(request.email()));

        if (found.isEmpty()) {
            passwordMatches(request.password(), dummyPasswordHash);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = found.get();
        if (user.isLoginLocked(now)) {
            audit.warn("event=LOGIN_REJECTED_LOCKED userId={}", user.getId());
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (!passwordMatches(request.password(), user.getPasswordHash())) {
            boolean locked = user.recordFailedLogin(properties.maxFailedLogins(), now.plus(properties.loginLockDuration()));
            audit.warn("event=LOGIN_FAILED userId={} locked={}", user.getId(), locked);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!user.isActive()) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }

        user.recordSuccessfulLogin();
        audit.info("event=LOGIN_SUCCEEDED userId={}", user.getId());
        return startSession(user, request.isRememberMe());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthSession refresh(String rawRefreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken);
        User user = rotation.user();
        return new AuthSession(user, jwtProvider.generateToken(user), rotation.token());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    @Transactional(readOnly = true)
    public UserDTO currentUser(UUID userId) {
        return userRepository.findById(userId)
                .map(UserDTO::from)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthSession startSession(User user, boolean rememberMe) {
        return new AuthSession(user, jwtProvider.generateToken(user), refreshTokenService.issue(user, rememberMe));
    }

    private boolean passwordMatches(String rawPassword, String hash) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            // Registration never accepts such passwords, so this cannot match.
            passwordEncoder.matches("", dummyPasswordHash);
            return false;
        }
        return passwordEncoder.matches(rawPassword, hash);
    }

    private static ApiException duplicateFrom(DataIntegrityViolationException e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                String constraint = violation.getConstraintName().toLowerCase(Locale.ROOT);
                if (constraint.contains("uk_users_email")) {
                    return new ApiException(ErrorCode.EMAIL_ALREADY_USED);
                }
                if (constraint.contains("uk_users_username")) {
                    return new ApiException(ErrorCode.USERNAME_ALREADY_USED);
                }
            }
            cause = cause.getCause();
        }
        throw e;
    }
}
