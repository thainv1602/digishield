package com.digishield.analytics.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Peer phish-prone rates the dashboard compares an organisation against.
 *
 * <p>These are <strong>reference figures supplied by whoever runs the
 * deployment</strong>, not measurements DigiShield takes. Nothing in the
 * product observes other organisations, and row-level security deliberately
 * keeps one tenant's data out of another tenant's queries, so an "industry
 * average" cannot be derived from what we store. The dashboard nevertheless
 * prints them next to a rate that <em>is</em> measured, which makes their
 * provenance a correctness question rather than a cosmetic one.
 *
 * <p>They previously sat as literals in {@link AnalyticsServiceImpl}, where
 * correcting a stale figure meant a code change and a redeploy, and where
 * nothing on screen or in the config told an operator the numbers were
 * assumptions. There is intentionally no default here: a figure the product
 * presents as fact has to be stated by someone who can vouch for it, so a
 * missing value stops the context from starting instead of quietly
 * substituting a plausible-looking number.
 *
 * @param industryAvgPct phish-prone rate for the sector the tenant is
 *                       benchmarked against; also the value returned as
 *                       {@code industry_avg_pct} by {@code /analytics/benchmark}
 *                       and {@code /analytics/dashboard}
 * @param financeAvgPct  phish-prone rate for the finance sector, shown as the
 *                       second peer bar on the dashboard
 */
@ConfigurationProperties(prefix = "digishield.analytics.benchmark")
public record BenchmarkProperties(Double industryAvgPct, Double financeAvgPct) {

    public BenchmarkProperties {
        requirePercentage("industry-avg-pct", industryAvgPct);
        requirePercentage("finance-avg-pct", financeAvgPct);
    }

    /**
     * Rejects both an absent rate and an out-of-range one.
     *
     * <p>The components are boxed on purpose. Constructor binding fills a
     * missing {@code double} with the primitive default, so a mistyped or
     * dropped key would have bound silently to {@code 0.0} — and a dashboard
     * claiming the sector average is 0% is a worse failure than one that will
     * not start. Boxed, an absent key arrives as null and is caught here.
     *
     * <p>A rate outside 0..100 would render a bar that runs off its track or
     * inverts, so it is rejected at startup rather than on screen. The negated
     * comparison also rejects NaN, which Jackson cannot serialise as JSON.
     */
    private static void requirePercentage(String name, Double value) {
        String property = "digishield.analytics.benchmark." + name;
        if (value == null) {
            throw new IllegalArgumentException(
                    property + " must be set: it is a reference figure DigiShield cannot derive "
                            + "from tenant data, so there is no honest default to fall back to.");
        }
        if (!(value >= 0.0 && value <= 100.0)) {
            throw new IllegalArgumentException(
                    property + " must be a percentage between 0 and 100, but was " + value);
        }
    }
}
