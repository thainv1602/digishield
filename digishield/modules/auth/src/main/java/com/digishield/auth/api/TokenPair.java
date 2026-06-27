package com.digishield.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Authentication token pair, matching the OpenAPI {@code TokenPair} schema.
 *
 * <p>JSON shape: {@code { "access_token": "...", "refresh_token": "...", "expires_in": 3600 }}.
 * In the dev profile these are static placeholder tokens (no real credential check).
 *
 * @param accessToken  the bearer access token
 * @param refreshToken the refresh token
 * @param expiresIn    access-token lifetime in seconds
 */
public record TokenPair(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") long expiresIn) {
}
