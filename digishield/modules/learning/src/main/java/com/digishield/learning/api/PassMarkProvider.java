package com.digishield.learning.api;

import java.util.UUID;

/**
 * SPI for the score a learner must reach to pass a quiz.
 * <p>
 * The value already existed as a per-tenant setting
 * ({@code business_thresholds.pass_score_pct}) that administrators could change
 * and nothing ever read — so the pass mark on screen and the pass mark applied
 * were unrelated. Bridged in the boot application to the tenancy module.
 */
public interface PassMarkProvider {

    /** Minimum score (percent) that counts as a pass for the tenant. */
    int passScorePct(UUID tenantId);
}
