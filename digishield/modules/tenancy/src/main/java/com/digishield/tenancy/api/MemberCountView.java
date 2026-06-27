package com.digishield.tenancy.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of evaluating a smart group ({@code POST /groups/{id}/evaluate}).
 *
 * @param memberCount number of members after the (re-)evaluation
 */
public record MemberCountView(
        @JsonProperty("member_count") int memberCount) {
}
