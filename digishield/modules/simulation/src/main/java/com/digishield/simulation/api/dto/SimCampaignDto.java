package com.digishield.simulation.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Summary view of a simulation campaign for the campaigns list.
 *
 * @param id         campaign identifier
 * @param name       human-readable campaign name (may be null)
 * @param channel    delivery channel (lowercase, e.g. "email")
 * @param status     lifecycle status (lowercase, e.g. "completed")
 * @param templateId content template identifier (may be null)
 */
public record SimCampaignDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("name") String name,
        @JsonProperty("channel") String channel,
        @JsonProperty("status") String status,
        @JsonProperty("templateId") UUID templateId) {
}
