package com.digishield.learning.infrastructure;

import com.digishield.learning.domain.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link QuizQuestion}.
 */
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

    List<QuizQuestion> findByTenantIdAndLessonIdOrderBySortOrderAsc(UUID tenantId, UUID lessonId);
}
