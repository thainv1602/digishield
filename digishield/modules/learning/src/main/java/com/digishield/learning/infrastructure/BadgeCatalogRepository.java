package com.digishield.learning.infrastructure;

import com.digishield.learning.domain.BadgeCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link BadgeCatalog}.
 */
public interface BadgeCatalogRepository extends JpaRepository<BadgeCatalog, UUID> {

    List<BadgeCatalog> findByTenantIdOrderByName(UUID tenantId);
}
