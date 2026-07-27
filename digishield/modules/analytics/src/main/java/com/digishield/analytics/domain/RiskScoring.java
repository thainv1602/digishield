package com.digishield.analytics.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

/**
 * The one definition of how a risk score is produced.
 * <p>
 * Two callers need it: the dashboard, scoring a single user on demand, and the
 * scheduled rollup, scoring everyone to aggregate by department. If each kept
 * its own copy of the baseline, the bounds and the window, they would drift, and
 * a department average would stop matching the per-user numbers shown next to
 * it — a discrepancy nobody could explain from the UI.
 */
public final class RiskScoring {

    /** Baseline risk for a user with no recent negative signals. */
    public static final int BASE_RISK = 5;
    /** Lower bound for a risk score. */
    public static final int MIN_RISK = 0;
    /** Upper bound for a risk score. */
    public static final int MAX_RISK = 100;
    /** Only signals from the last {@code SCORING_WINDOW} count toward a score. */
    public static final Duration SCORING_WINDOW = Duration.ofDays(90);

    private RiskScoring() {
    }

    /** Start of the scoring window relative to {@code now}. */
    public static Instant windowStart(Instant now) {
        return now.minus(SCORING_WINDOW);
    }

    /**
     * Score for one user: the baseline plus the summed weight of their signals,
     * clamped. Risky actions (e.g. simulation clicks) carry positive weight;
     * vigilant ones (e.g. confirmed phishing reports) carry negative weight.
     * Higher means more phish-prone.
     *
     * @param signals that user's signals from within the window
     */
    public static int score(Collection<RiskSignal> signals) {
        int weight = signals.stream().mapToInt(RiskSignal::getWeight).sum();
        return clamp(BASE_RISK + weight);
    }

    /** Constrains a value to the representable risk range. */
    public static int clamp(int value) {
        return Math.max(MIN_RISK, Math.min(MAX_RISK, value));
    }
}
