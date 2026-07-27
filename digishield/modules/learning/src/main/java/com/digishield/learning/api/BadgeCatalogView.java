package com.digishield.learning.api;

import java.util.UUID;

/**
 * Public view of a badge definition in the tenant's badge catalog. Also used as
 * the request body for creating one ({@code id} ignored on create).
 */
public record BadgeCatalogView(UUID id, String name, String description, String iconRef,
                               @com.fasterxml.jackson.annotation.JsonProperty("criteria_type")
                               String criteriaType,
                               @com.fasterxml.jackson.annotation.JsonProperty("criteria_threshold")
                               Integer criteriaThreshold) {

    /**
     * A badge with no criteria: nothing awards it automatically.
     * <p>
     * Kept so existing callers compile unchanged, and because a catalogue entry
     * without a rule is a real state rather than an oversight — it is what every
     * row looked like before criteria existed.
     */
    public BadgeCatalogView(UUID id, String name, String description, String iconRef) {
        this(id, name, description, iconRef, null, null);
    }
}
