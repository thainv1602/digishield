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

    Optional<Lesson> findByTenantIdAndId(UUID tenantId, UUID id);
}
