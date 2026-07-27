package com.digishield.learning.infrastructure;

import com.digishield.learning.domain.PointRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link PointRule}.
 */
public interface PointRuleRepository extends JpaRepository<PointRule, UUID> {

    List<PointRule> findByTenantIdOrderByPointsDesc(UUID tenantId);

    /** A tenant's own value for one action, overriding the built-in default. */
    java.util.Optional<PointRule> findByTenantIdAndAction(UUID tenantId, String action);
}
