package com.digishield.simulation.infrastructure;

import com.digishield.simulation.domain.SimEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository cho {@link SimEvent}.
 */
public interface SimEventRepository extends JpaRepository<SimEvent, UUID> {

    List<SimEvent> findByTenantIdAndCampaignId(UUID tenantId, UUID campaignId);
}
