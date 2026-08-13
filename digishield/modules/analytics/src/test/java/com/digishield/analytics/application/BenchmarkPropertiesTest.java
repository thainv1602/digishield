package com.digishield.analytics.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The peer rates are drawn as bars on a 0..100 track, so a value outside that
 * range renders as a bar that overshoots its track or reverses. Rejecting it
 * when the context starts turns a silent visual lie into a startup failure that
 * names the offending property.
 */
class BenchmarkPropertiesTest {

    @Test
    void acceptsPercentagesInRange() {
        BenchmarkProperties properties = new BenchmarkProperties(11.2, 14.8);

        assertThat(properties.industryAvgPct()).isEqualTo(11.2);
        assertThat(properties.financeAvgPct()).isEqualTo(14.8);
    }

    @Test
    void acceptsTheBoundaries() {
        assertThat(new BenchmarkProperties(0.0, 100.0).industryAvgPct()).isZero();
        assertThat(new BenchmarkProperties(0.0, 100.0).financeAvgPct()).isEqualTo(100.0);
    }

    @Test
    void rejectsANegativeIndustryAverage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BenchmarkProperties(-0.1, 14.8))
                .withMessageContaining("digishield.analytics.benchmark.industry-avg-pct")
                .withMessageContaining("-0.1");
    }

    @Test
    void rejectsARateAboveOneHundred() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BenchmarkProperties(11.2, 100.01))
                .withMessageContaining("digishield.analytics.benchmark.finance-avg-pct");
    }

    @Test
    void rejectsAnAbsentRate() {
        // Constructor binding hands null for a key nobody wrote. If these were
        // primitives it would hand 0.0 instead, and the dashboard would state
        // that the sector average is zero.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BenchmarkProperties(null, 14.8))
                .withMessageContaining("digishield.analytics.benchmark.industry-avg-pct")
                .withMessageContaining("must be set");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BenchmarkProperties(11.2, null))
                .withMessageContaining("digishield.analytics.benchmark.finance-avg-pct");
    }

    @Test
    void rejectsNaN() {
        // A bare `value < 0 || value > 100` check would let NaN through, and NaN
        // reaches the wire as a JSON literal Jackson cannot emit.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BenchmarkProperties(Double.NaN, 14.8))
                .withMessageContaining("industry-avg-pct");
    }
}
