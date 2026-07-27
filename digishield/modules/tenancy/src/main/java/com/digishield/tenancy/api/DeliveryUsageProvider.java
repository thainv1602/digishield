package com.digishield.tenancy.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * SPI reporting how many messages a tenant actually sent, so usage can be
 * metered from what happened rather than seeded.
 * <p>
 * Tenancy owns the meter; the notification module owns the record of what was
 * sent. The boot application bridges them, as it does for every other
 * cross-module read here.
 */
public interface DeliveryUsageProvider {

    /**
     * Messages the tenant successfully sent in the window, keyed by the metric
     * name used in {@code usage_metering} — {@code email_sent}, {@code sms_sent}.
     * <p>
     * Only messages that were sent count. An attempt that failed cost the tenant
     * nothing and delivered nothing, so metering it would overstate both.
     *
     * @param from inclusive start of the window
     * @param to   exclusive end
     */
    Map<String, Long> sentCounts(UUID tenantId, Instant from, Instant to);
}
