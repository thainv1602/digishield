package com.digishield.tenancy.infrastructure;

import com.digishield.tenancy.domain.UsageMetering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link UsageMetering}.
 */
public interface UsageMeteringRepository extends JpaRepository<UsageMetering, UUID> {

    /**
     * Get all usage data points of a tenant (any period).
     */
    List<UsageMetering> findByTenantId(UUID tenantId);

    /**
     * Get the usage data points of a tenant for a specific billing period.
     */
    List<UsageMetering> findByTenantIdAndPeriod(UUID tenantId, String period);
}
