package com.digishield.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * User list/detail view returned by {@code GET /api/v1/users}.
 *
 * <p>Aligned with the OpenAPI {@code User} schema (snake_case: {@code org_id},
 * {@code risk_score}, {@code role} as the snake_case wire role) while also
 * exposing the {@code name}, {@code department} and {@code riskScore} fields the
 * frontend Users screen reads.
 *
 * @param id         user id
 * @param orgId      tenant / organization id (OpenAPI {@code org_id})
 * @param email      login email
 * @param name       display name
 * @param role       snake_case role (e.g. {@code org_admin})
 * @param status     account status (lower-case)
 * @param department department / org-unit label
 * @param riskScore  cached risk score 0..100 (camelCase, for the FE)
 * @param riskScoreSnake same value as {@code risk_score} for OpenAPI consumers
 */
public record UserView(
        UUID id,
        @JsonProperty("org_id") UUID orgId,
        String email,
        String name,
        String role,
        String status,
        String department,
        @JsonProperty("riskScore") Integer riskScore,
        @JsonProperty("risk_score") Integer riskScoreSnake) {

    public static UserView of(UUID id, UUID orgId, String email, String name,
                              String role, String status, String department, Integer riskScore) {
        return new UserView(id, orgId, email, name, role, status, department, riskScore, riskScore);
    }
}
