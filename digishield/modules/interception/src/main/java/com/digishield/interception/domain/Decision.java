package com.digishield.interception.domain;

/**
 * Transaction intervention decision.
 */
public enum Decision {
    /** Allow to proceed. */
    ALLOW,
    /** Warn but still allow. */
    WARN,
    /** Pause for the user to confirm. */
    PAUSE,
    /** Block the transaction. */
    BLOCK
}
