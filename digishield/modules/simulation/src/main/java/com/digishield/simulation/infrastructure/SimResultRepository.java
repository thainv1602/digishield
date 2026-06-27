package com.digishield.simulation.infrastructure;

import com.digishield.simulation.domain.SimResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link SimResult}.
 */
public interface SimResultRepository extends JpaRepository<SimResult, UUID> {

    List<SimResult> findByTenantIdAndCampaignId(UUID tenantId, UUID campaignId);
}
