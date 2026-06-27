package com.digishield.tenancy.infrastructure;

import com.digishield.tenancy.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Tenant}.
 */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    /**
     * Find a tenant by its business identifier (tenantId).
     */
    Optional<Tenant> findByTenantId(UUID tenantId);
}
