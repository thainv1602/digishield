package com.digishield.analytics.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring the analytics module owns.
 *
 * <p>Registering {@link BenchmarkProperties} here rather than on the boot class
 * keeps the module self-contained: {@code boot/app} does not have to know which
 * settings any individual module reads.
 */
@Configuration
@EnableConfigurationProperties(BenchmarkProperties.class)
class AnalyticsConfiguration {
}
