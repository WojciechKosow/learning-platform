package com.project.learning_platform.entity;

public enum UserStatus {
    /** Can log in and use the platform. */
    ACTIVE,
    /** Blocked by an administrator; cannot log in, existing sessions stop working. */
    DISABLED
}
