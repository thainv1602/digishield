package com.digishield.tenancy.application;

import com.digishield.tenancy.api.DeliveryUsageProvider;
import com.digishield.tenancy.api.TenancyService;
import com.digishield.tenancy.domain.UsageMetering;
import com.digishield.tenancy.infrastructure.UsageMeteringRepository;
import com.digishield.shared.tenantcontext.TenantContext;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records what each tenant actually sent this month.
 * <p>
 * {@code usage_metering} had one writer, a {@code @Profile("dev")} seeder, whose
 * numbers (184,200 emails; 12,400 SMS) were invented. A real deployment read the
 * table empty, so the usage screen showed nothing while the platform was in fact
 * sending.
 * <p>
 * Only what can be counted is written. The seeder also carried {@code ai_call}
 * and {@code storage}; nothing records either, so no row is produced for them.
 * An absent metric reads as "not measured", which is true — a zero would read as
 * "measured, and it was none", which is not.
 * <p>
 * Runs in the {@code scheduler} profile alongside the risk rollup, single
 * replica, for the same reason: two of these would overwrite each other's
 * figures.
 */
@Component
@Profile("scheduler")
public class UsageMeteringJob {

    private static final Logger LOG = LoggerFactory.getLogger(UsageMeteringJob.class);

    private final TenancyService tenancyService;
    private final DeliveryUsageProvider deliveryUsage;
    private final UsageMeteringRepository usageMeteringRepository;
    private final ZoneId zone;

    public UsageMeteringJob(TenancyService tenancyService,
                            DeliveryUsageProvider deliveryUsage,
                            UsageMeteringRepository usageMeteringRepository,
                            @Value("${digishield.tenancy.usage-metering.zone:Asia/Ho_Chi_Minh}")
                            String zone) {
        this.tenancyService = tenancyService;
        this.deliveryUsage = deliveryUsage;
        this.usageMeteringRepository = usageMeteringRepository;
        this.zone = ZoneId.of(zone);
    }

    /**
     * Hourly rather than nightly: usage drives billing limits, and a figure that
     * is a day stale is a day of sending nobody could see.
     */
    @Scheduled(cron = "${digishield.tenancy.usage-metering.cron:0 40 * * * *}",
            zone = "${digishield.tenancy.usage-metering.zone:Asia/Ho_Chi_Minh}")
    public void run() {
        List<UUID> tenantIds = tenancyService.systemActiveTenantIds();
        if (tenantIds.isEmpty()) {
            LOG.error("Usage metering found no active tenants — usage will not be updated.");
            return;
        }
        YearMonth month = YearMonth.now(zone);
        int done = 0;
        for (UUID tenantId : tenantIds) {
            String previousTenant = TenantContext.get();
            TenantContext.set(tenantId.toString());
            try {
                meter(tenantId, month);
                done++;
            } catch (RuntimeException e) {
                // One tenant's failure must not stop the rest; a job that stops
                // halfway leaves some figures current and others stale with
                // nothing saying which.
                LOG.error("Usage metering failed for tenant {}", tenantId, e);
            } finally {
                TenantContext.restore(previousTenant);
            }
        }
        LOG.info("Usage metering finished for {} of {} tenant(s), period {}",
                done, tenantIds.size(), month);
    }

    @Transactional
    void meter(UUID tenantId, YearMonth month) {
        Instant from = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        String period = month.toString();

        Map<String, Long> counts = deliveryUsage.sentCounts(tenantId, from, to);
        List<UsageMetering> existing = usageMeteringRepository.findByTenantIdAndPeriod(tenantId, period);

        counts.forEach((metric, value) -> {
            // Rewritten in place rather than appended: this is the running total
            // for the month, not a history, and a second row would double the
            // tenant's usage against its plan limit.
            UsageMetering row = existing.stream()
                    .filter(u -> metric.equals(u.getMetric()))
                    .findFirst()
                    .orElseGet(() -> new UsageMetering(UUID.randomUUID(), tenantId, metric, 0, period));
            row.setValue(value);
            usageMeteringRepository.save(row);
        });
        LOG.debug("Usage for tenant {} in {}: {}", tenantId, period, counts);
    }
}
