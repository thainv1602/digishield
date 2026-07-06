package com.digishield.analytics.infrastructure;

import com.digishield.analytics.domain.RiskScope;
import com.digishield.analytics.domain.RiskScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Repository cho {@link RiskScore}.
 */
public interface RiskScoreRepository extends JpaRepository<RiskScore, UUID> {

    List<RiskScore> findByTenantIdAndScope(UUID tenantId, RiskScope scope);

    List<RiskScore> findByTenantIdAndScopeAndScopeId(UUID tenantId, RiskScope scope, UUID scopeId);

    /**
     * Distinct scope ids (user ids for {@link RiskScope#USER}) that have a risk
     * score in the tenant — the set of users an org-wide recompute iterates over.
     */
    @Query("select distinct r.scopeId from RiskScore r "
            + "where r.tenantId = :tenantId and r.scope = :scope")
    List<UUID> findDistinctScopeIds(@Param("tenantId") UUID tenantId, @Param("scope") RiskScope scope);
}
