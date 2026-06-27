package com.digishield.simulation.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/**
 * Detailed view of a simulation campaign: summary + funnel counts + per-user
 * result rows. Powers the campaign results screen.
 */
public record SimCampaignDetailDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("name") String name,
        @JsonProperty("channel") String channel,
        @JsonProperty("status") String status,
        @JsonProperty("templateId") UUID templateId,
        @JsonProperty("funnel") Funnel funnel,
        @JsonProperty("results") List<ResultRow> results) {

    /**
     * Aggregated funnel counts for the campaign.
     */
    public record Funnel(
            @JsonProperty("delivered") long delivered,
            @JsonProperty("open") long open,
            @JsonProperty("click") long click,
            @JsonProperty("submit") long submit,
            @JsonProperty("report") long report) {
    }

    /**
     * A single per-user result row.
     *
     * @param name           user display name
     * @param department     user department
     * @param action         final/most-severe action (lowercase, e.g. "click")
     * @param learningStatus remediation learning status (lowercase)
     */
    public record ResultRow(
            @JsonProperty("name") String name,
            @JsonProperty("department") String department,
            @JsonProperty("action") String action,
            @JsonProperty("learningStatus") String learningStatus) {
    }
}
