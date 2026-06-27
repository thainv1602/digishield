package com.digishield.learning.infrastructure;

import com.digishield.learning.domain.CompliancePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CompliancePolicy}.
 */
public interface CompliancePolicyRepository extends JpaRepository<CompliancePolicy, UUID> {

    List<CompliancePolicy> findByTenantId(UUID tenantId);
}
