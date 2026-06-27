package com.digishield.analytics.infrastructure;

import com.digishield.analytics.domain.RiskScope;
import com.digishield.analytics.domain.RiskScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository cho {@link RiskScore}.
 */
public interface RiskScoreRepository extends JpaRepository<RiskScore, UUID> {

    List<RiskScore> findByTenantIdAndScope(UUID tenantId, RiskScope scope);

    List<RiskScore> findByTenantIdAndScopeAndScopeId(UUID tenantId, RiskScope scope, UUID scopeId);
}
