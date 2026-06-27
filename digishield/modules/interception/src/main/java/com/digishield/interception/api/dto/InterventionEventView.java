package com.digishield.interception.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Intervention log entry view, matching the OpenAPI {@code InterventionEvent} schema.
 * <p>
 * Wire field names are snake_case ({@code org_id}, {@code user_id}). The {@code decision}
 * and {@code signals} values are emitted as lowercase strings to match the frontend
 * ({@code decision} of allow|warn|pause|block).
 *
 * @param id       event identifier
 * @param orgId    owning tenant (org) identifier
 * @param userId   the user who performed the transaction
 * @param signals  detected signals (e.g. on_call, new_payee, watchlist_hit)
 * @param decision the decision taken (allow|warn|pause|block)
 * @param ts       timestamp the decision was recorded
 */
public record InterventionEventView(
        @JsonProperty("id") UUID id,
        @JsonProperty("org_id") UUID orgId,
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("signals") List<String> signals,
        @JsonProperty("decision") String decision,
        @JsonProperty("ts") Instant ts) {
}
