package com.digishield.simulation.infrastructure;

import com.digishield.simulation.domain.SimRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link SimRecipient}. Tenant-scoped reads only;
 * the public tracking endpoint resolves a token via a superuser JdbcTemplate
 * read that bypasses RLS (there is no tenant context on that request).
 */
public interface SimRecipientRepository extends JpaRepository<SimRecipient, UUID> {

    List<SimRecipient> findByTenantIdAndCampaignId(UUID tenantId, UUID campaignId);
}
