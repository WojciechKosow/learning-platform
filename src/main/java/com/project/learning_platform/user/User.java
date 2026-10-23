package com.project.learning_platform.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * The account: identity, credentials and account state only.
 *
 * <p>Learning data (preferences, onboarding, skill profile, projects, GitHub
 * connection, subscription, ...) belongs in separate tables that reference
 * {@code users.id} — keep this entity small.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Always stored lower-cased (see {@code AuthService#normalizeEmail}). */
    @Column(nullable = false, length = 254)
    private String email;

    /** Public handle; case is preserved, uniqueness is case-insensitive. */
    @Column(nullable = false, length = 30)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    // Brute-force protection: counted on every failed login, reset on success.
    // Reaching the limit locks password login until lockedUntil.
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected User() {
        // for JPA
    }

    public User(String email, String username, String passwordHash) {
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = UserRole.USER;
        this.status = UserStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        // PostgreSQL stores microseconds; truncate so the API returns the same value before and after a reload.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isLoginLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Records a failed password attempt.
     *
     * @return {@code true} if this attempt reached the limit and locked the account
     */
    public boolean recordFailedLogin(int maxAttempts, Instant lockUntil) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            failedLoginAttempts = 0;
            lockedUntil = lockUntil;
            return true;
        }
        return false;
    }

    public void recordSuccessfulLogin() {
        // Avoid a write (and a version bump) on the common path.
        if (failedLoginAttempts != 0 || lockedUntil != null) {
            failedLoginAttempts = 0;
            lockedUntil = null;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
