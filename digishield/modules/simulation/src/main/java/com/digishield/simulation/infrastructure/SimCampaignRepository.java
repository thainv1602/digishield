package com.digishield.simulation.infrastructure;

import com.digishield.simulation.domain.SimCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository cho {@link SimCampaign}.
 */
public interface SimCampaignRepository extends JpaRepository<SimCampaign, UUID> {

    List<SimCampaign> findByTenantId(UUID tenantId);
}
