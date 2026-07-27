package com.digishield.analytics.application;

import com.digishield.analytics.api.TenantDirectory;
import com.digishield.shared.tenantcontext.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives {@link RiskRollupService} across every tenant on a schedule.
 * <p>
 * Only active in the {@code scheduler} profile, which runs as a single replica.
 * The api and worker deployments must not also run this: several replicas
 * rolling up the same tenant at the same time would each delete and rewrite
 * {@code department_risk} underneath the others.
 */
@Component
@Profile("scheduler")
public class RiskRollupJob {

    private static final Logger log = LoggerFactory.getLogger(RiskRollupJob.class);

    private final TenantDirectory tenants;
    private final RiskRollupService rollupService;
    private final String cron;

    public RiskRollupJob(TenantDirectory tenants,
                         RiskRollupService rollupService,
                         @Value("${digishield.analytics.risk-rollup.cron:0 20 2 * * *}") String cron) {
        this.tenants = tenants;
        this.rollupService = rollupService;
        this.cron = cron;
    }

    /**
     * Recomputes every tenant's aggregates. Defaults to nightly, well after the
     * working day, since a rollup rewrites what the dashboard reads and the
     * inputs only change as people act on simulations.
     */
    @Scheduled(cron = "${digishield.analytics.risk-rollup.cron:0 20 2 * * *}",
            zone = "${digishield.analytics.risk-rollup.zone:Asia/Ho_Chi_Minh}")
    public void run() {
        Instant now = Instant.now();
        List<UUID> tenantIds = tenants.activeTenantIds();
        if (tenantIds.isEmpty()) {
            // A running system always has at least one active tenant, so this is
            // a fault, not a quiet day. The enumeration reads the tenant registry
            // outside tenant isolation, which currently works because the app
            // connects as the database owner; were that to change, the query
            // would return nothing and this job would do nothing every night
            // while still logging a clean finish. Say so loudly instead.
            log.error("Risk rollup found no active tenants — dashboards will not be updated. "
                    + "Check that the tenant registry is readable outside tenant isolation.");
            return;
        }
        log.info("Risk rollup starting for {} tenant(s) (cron {})", tenantIds.size(), cron);

        int done = 0;
        int failed = 0;
        for (UUID tenantId : tenantIds) {
            TenantContext.set(tenantId.toString());
            try {
                rollupService.rollup(now);
                done++;
            } catch (RuntimeException e) {
                // One tenant's bad data must not stop the rest from being rolled
                // up; a job that aborts halfway leaves some dashboards fresh and
                // others stale with nothing saying which is which.
                failed++;
                log.error("Risk rollup failed for tenant {}", tenantId, e);
            } finally {
                TenantContext.clear();
            }
        }
        log.info("Risk rollup finished: {} succeeded, {} failed", done, failed);
    }
}
