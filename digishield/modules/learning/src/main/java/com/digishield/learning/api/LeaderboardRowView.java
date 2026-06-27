package com.digishield.learning.api;

/**
 * A leaderboard row (matches the OpenAPI gamification leaderboard item shape).
 *
 * @param rank   1-based rank
 * @param name   display name
 * @param points accumulated points
 */
public record LeaderboardRowView(int rank, String name, int points) {
}
