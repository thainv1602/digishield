package com.digishield.learning.infrastructure;

import com.digishield.learning.domain.Enrollment;
import com.digishield.learning.domain.EnrollmentStatus;
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
     * How many enrollments a tenant has, for the completion percentage.
     *
     * <p>The dashboard used to fetch every enrollment (and every course, to
     * label them) and count in Java, so a tile showing one number cost two
     * table loads that grew with the tenant.
     */
    long countByTenantId(UUID tenantId);

    /**
     * How many of a tenant's enrollments are in a given status.
     *
     * <p>Kept as a second count rather than folded into one query with a CASE:
     * two derived counts read plainly, both are answered from
     * {@code idx_enrollment_tenant_status}, and the caller can skip this one
     * entirely when the tenant has no enrollments at all.
     */
    long countByTenantIdAndStatus(UUID tenantId, EnrollmentStatus status);

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

    /**
     * Assignments with a deadline that are not finished, for the overdue sweep.
     *
     * <p>Not scoped to a tenant: the sweep runs across all of them, and asking
     * per tenant would mean one query per tenant to find the handful that are
     * late.
     */
    List<Enrollment> findByDueAtIsNotNullAndStatusNot(EnrollmentStatus status);


    /** Whether anyone is enrolled on a course, so deleting one cannot orphan progress. */
    boolean existsByTenantIdAndCourseId(UUID tenantId, UUID courseId);
}
