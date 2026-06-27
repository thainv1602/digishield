package com.digishield.interception.domain;

/**
 * Risk level of a watchlist entry.
 */
public enum RiskLevel {
    /** Needs monitoring. */
    WATCH,
    /** High risk. */
    HIGH,
    /** Confirmed fraud. */
    CONFIRMED
}
