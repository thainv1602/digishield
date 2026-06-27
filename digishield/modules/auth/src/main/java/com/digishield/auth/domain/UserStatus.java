package com.digishield.auth.domain;

/**
 * Lifecycle status of a user account.
 */
public enum UserStatus {
    /** Created but not yet activated. */
    PENDING,
    /** Operating normally. */
    ACTIVE,
    /** Temporarily locked. */
    SUSPENDED,
    /** Permanently disabled. */
    DISABLED
}
