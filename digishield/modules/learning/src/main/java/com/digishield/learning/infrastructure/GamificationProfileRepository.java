package com.digishield.learning.infrastructure;

import com.digishield.learning.domain.GamificationProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link GamificationProfile}.
 */
public interface GamificationProfileRepository extends JpaRepository<GamificationProfile, UUID> {

    List<GamificationProfile> findByTenantIdOrderByPointsDesc(UUID tenantId);

    Optional<GamificationProfile> findByTenantIdAndUserId(UUID tenantId, UUID userId);
}
