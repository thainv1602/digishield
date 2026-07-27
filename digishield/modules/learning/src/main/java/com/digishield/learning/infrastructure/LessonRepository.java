package com.digishield.learning.infrastructure;

import com.digishield.learning.domain.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Lesson}.
 */
public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    List<Lesson> findByTenantIdAndCourseIdOrderBySortOrderAsc(UUID tenantId, UUID courseId);

    List<Lesson> findByTenantIdOrderBySortOrderAsc(UUID tenantId);

    Optional<Lesson> findByTenantIdAndId(UUID tenantId, UUID id);

    /** Keeps {@code course.lesson_count} honest as lessons come and go. */
    int countByTenantIdAndCourseId(UUID tenantId, UUID courseId);

    /** Lessons belong to their course and are meaningless once it is gone. */
    void deleteByTenantIdAndCourseId(UUID tenantId, UUID courseId);
}
