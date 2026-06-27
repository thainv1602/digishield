package com.digishield.simulation.infrastructure;

import com.digishield.simulation.domain.SimCampaignFunnel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link SimCampaignFunnel}.
 */
public interface SimCampaignFunnelRepository extends JpaRepository<SimCampaignFunnel, UUID> {

    Optional<SimCampaignFunnel> findByTenantIdAndCampaignId(UUID tenantId, UUID campaignId);
}
