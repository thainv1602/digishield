package com.digishield.learning.infrastructure;

import com.digishield.learning.domain.Assessment;
import com.digishield.learning.domain.AssessmentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Assessment}.
 */
public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    List<Assessment> findByTenantId(UUID tenantId);

    List<Assessment> findByTenantIdAndType(UUID tenantId, AssessmentType type);

    Optional<Assessment> findByTenantIdAndId(UUID tenantId, UUID id);
}
