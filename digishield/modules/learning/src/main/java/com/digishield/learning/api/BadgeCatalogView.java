package com.digishield.learning.api;

import java.util.UUID;

/**
 * Public view of a badge definition in the tenant's badge catalog. Also used as
 * the request body for creating one ({@code id} ignored on create).
 */
public record BadgeCatalogView(UUID id, String name, String description, String iconRef) {
}
