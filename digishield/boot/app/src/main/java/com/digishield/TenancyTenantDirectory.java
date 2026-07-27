package com.digishield;

import com.digishield.analytics.api.TenantDirectory;
import com.digishield.tenancy.api.TenancyService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Wires the analytics module's {@link TenantDirectory} SPI to tenancy, so the
 * nightly risk rollup can learn which tenants it must visit.
 * <p>
 * This is the one read in the rollup that cannot be tenant-scoped: a scheduled
 * job has to discover the tenants before it can adopt one. Tenancy guards that
 * with {@code SystemScope}, which opens RLS only off a request thread and only
 * around this query — everything the rollup does afterwards runs under a tenant
 * like any request would.
 */
@Component
class TenancyTenantDirectory implements TenantDirectory {

    private final TenancyService tenancyService;

    TenancyTenantDirectory(TenancyService tenancyService) {
        this.tenancyService = tenancyService;
    }

    @Override
    public List<UUID> activeTenantIds() {
        return tenancyService.systemActiveTenantIds();
    }
}
