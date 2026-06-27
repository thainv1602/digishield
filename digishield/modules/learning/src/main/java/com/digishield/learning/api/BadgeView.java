package com.digishield.learning.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Public view describing a gamification badge.
 *
 * @param id          badge identifier
 * @param name        badge name
 * @param description badge description / earning criteria
 * @param iconRef     icon hint (e.g. "shield", "target", "zap")
 * @param earned      whether the user has earned the badge
 * @param awardedAt   award timestamp (null if not earned)
 */
public record BadgeView(UUID id, String name, String description, String iconRef,
                        boolean earned, Instant awardedAt) {
}
