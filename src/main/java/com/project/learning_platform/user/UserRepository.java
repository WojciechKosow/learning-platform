package com.project.learning_platform.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** {@code email} must already be normalized (lower-cased). */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsernameIgnoreCase(String username);
}
