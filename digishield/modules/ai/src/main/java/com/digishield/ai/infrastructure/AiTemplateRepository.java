package com.digishield.ai.infrastructure;

import com.digishield.ai.domain.AiTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link AiTemplate} drafts.
 */
public interface AiTemplateRepository extends JpaRepository<AiTemplate, UUID> {

    List<AiTemplate> findByTenantId(UUID tenantId);
}
