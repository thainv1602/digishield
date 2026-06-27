package com.digishield.learning.infrastructure;

import com.digishield.learning.domain.Badge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Badge}.
 */
public interface BadgeRepository extends JpaRepository<Badge, UUID> {

    List<Badge> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    List<Badge> findByTenantId(UUID tenantId);
}
