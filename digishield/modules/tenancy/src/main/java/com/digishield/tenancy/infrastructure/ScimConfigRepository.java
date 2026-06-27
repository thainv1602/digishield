package com.digishield.tenancy.infrastructure;

import com.digishield.tenancy.domain.ScimConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ScimConfig}.
 */
public interface ScimConfigRepository extends JpaRepository<ScimConfig, UUID> {

    Optional<ScimConfig> findByTenantId(UUID tenantId);
}
