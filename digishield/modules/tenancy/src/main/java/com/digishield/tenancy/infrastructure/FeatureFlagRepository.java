package com.digishield.tenancy.infrastructure;

import com.digishield.tenancy.domain.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link FeatureFlag}.
 */
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

    /**
     * Get all feature flags of a tenant.
     */
    List<FeatureFlag> findByTenantId(UUID tenantId);

    /**
     * Get a specific feature flag by key within the scope of a tenant.
     */
    Optional<FeatureFlag> findByTenantIdAndKey(UUID tenantId, String key);
}
