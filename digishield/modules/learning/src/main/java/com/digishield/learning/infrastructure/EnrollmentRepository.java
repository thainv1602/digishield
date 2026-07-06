package com.digishield.learning.infrastructure;

import com.digishield.learning.domain.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Enrollment}.
 */
public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    /**
     * Number of distinct users enrolled in any training within the tenant — the
     * population denominator for the compliance KPIs.
     */
    @Query("select count(distinct e.userId) from Enrollment e where e.tenantId = :tenantId")
    long countDistinctUsers(@Param("tenantId") UUID tenantId);

    /**
     * Gets a user's enrollments within the scope of a tenant.
     */
    List<Enrollment> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    /**
     * Gets all enrollments of a tenant (used by the Learner portal / catalog).
     */
    List<Enrollment> findByTenantId(UUID tenantId);

    /**
     * Gets all enrollments of a tenant filtered by status.
     */
    List<Enrollment> findByTenantIdAndStatus(UUID tenantId,
            com.digishield.learning.domain.EnrollmentStatus status);

    /**
     * Finds a specific enrollment of a user into a course.
     */
    Optional<Enrollment> findByTenantIdAndUserIdAndCourseId(UUID tenantId, UUID userId, UUID courseId);

    Optional<Enrollment> findByTenantIdAndId(UUID tenantId, UUID id);
}
