package com.digishield.learning.api;

import java.util.UUID;

/**
 * Lightweight wire view of a lesson for list screens (returned by
 * {@code GET /api/v1/lessons}). Plain record → camelCase JSON.
 *
 * <p>{@code questionCount} lets the client tell which lessons have a quiz to
 * take, so the learner "Bài kiểm tra" page can link straight to
 * {@code /learn/quiz/{id}} ({@code GET /lessons/{id}/quiz}).
 *
 * @param id            lesson identifier (also the quiz id)
 * @param courseId      owning course id
 * @param courseTitle   owning course title (may be null)
 * @param title         lesson title
 * @param durationMin   estimated duration in minutes (may be null)
 * @param questionCount number of quiz questions attached to the lesson
 */
public record LessonSummaryView(
        UUID id,
        UUID courseId,
        String courseTitle,
        String title,
        Integer durationMin,
        int questionCount) {
}
