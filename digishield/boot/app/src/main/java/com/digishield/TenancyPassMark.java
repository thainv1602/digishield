package com.digishield;

import com.digishield.learning.api.PassMarkProvider;
import com.digishield.tenancy.api.TenancyService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Wires the learning module's {@link PassMarkProvider} SPI to the tenant's
 * configured business thresholds, so changing the pass score in settings
 * actually changes what passes.
 */
@Component
class TenancyPassMark implements PassMarkProvider {

    private static final int DEFAULT_PASS_SCORE_PCT = 70;

    private final TenancyService tenancyService;

    TenancyPassMark(TenancyService tenancyService) {
        this.tenancyService = tenancyService;
    }

    @Override
    public int passScorePct(UUID tenantId) {
        Integer configured = tenancyService.getThresholds(tenantId).passScorePct();
        // getThresholds creates defaults on first read, so this is normally set;
        // the fallback only covers a tenant whose row predates that.
        return configured != null ? configured : DEFAULT_PASS_SCORE_PCT;
    }
}
