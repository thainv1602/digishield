package com.digishield.learning.infrastructure;

import com.digishield.learning.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Course}.
 */
public interface CourseRepository extends JpaRepository<Course, UUID> {

    /**
     * Gets the list of courses for a tenant.
     */
    List<Course> findByTenantId(UUID tenantId);

    /**
     * Gets the tenant's courses ordered by their catalog position.
     */
    List<Course> findByTenantIdOrderBySortOrderAsc(UUID tenantId);

    Optional<Course> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Gets the tenant's first course (used for the sample auto-assign flow).
     */
    Optional<Course> findFirstByTenantId(UUID tenantId);
}
