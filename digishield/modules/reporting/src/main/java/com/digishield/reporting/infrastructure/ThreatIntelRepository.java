package com.digishield.reporting.infrastructure;

import com.digishield.reporting.domain.ThreatIntel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository cho {@link ThreatIntel}.
 */
public interface ThreatIntelRepository extends JpaRepository<ThreatIntel, UUID> {

    List<ThreatIntel> findByTenantId(UUID tenantId);

    List<ThreatIntel> findByTenantIdOrderByCollectedAtDesc(UUID tenantId);
}
