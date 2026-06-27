package com.digishield.analytics.infrastructure;

import com.digishield.analytics.domain.DepartmentRisk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link DepartmentRisk}.
 */
public interface DepartmentRiskRepository extends JpaRepository<DepartmentRisk, UUID> {

    List<DepartmentRisk> findByTenantIdOrderByRiskScoreDesc(UUID tenantId);
}
