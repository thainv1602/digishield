package com.digishield.analytics.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the property names the deployment writes against.
 *
 * <p>The record has no defaults, so a renamed or mistyped key does not fall
 * back to anything — it stops the context. That is the intended failure mode,
 * but it only helps if the names in application.yml and the names Spring binds
 * are the same, which is what these two tests check.
 */
class BenchmarkPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AnalyticsConfiguration.class);

    @Test
    void bindsBothRatesFromTheConfiguredPrefix() {
        contextRunner
                .withPropertyValues(
                        "digishield.analytics.benchmark.industry-avg-pct=12.5",
                        "digishield.analytics.benchmark.finance-avg-pct=16.0")
                .run(context -> assertThat(context).hasNotFailed()
                        .getBean(BenchmarkProperties.class)
                        .isEqualTo(new BenchmarkProperties(12.5, 16.0)));
    }

    @Test
    void refusesToStartWhenARateIsMissing() {
        // Half-configured is the dangerous case: with a default in place the
        // dashboard would show one real figure beside one invented one.
        contextRunner
                .withPropertyValues("digishield.analytics.benchmark.industry-avg-pct=12.5")
                .run(context -> assertThat(context).hasFailed());
    }
}
