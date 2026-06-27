package com.digishield.tenancy.api;

/**
 * Public view of a feature flag.
 *
 * @param key     key of the flag
 * @param enabled whether the flag is enabled
 */
public record FeatureFlagView(String key, boolean enabled) {
}
