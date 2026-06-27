package com.digishield.learning.api;

import java.util.List;
import java.util.UUID;

/**
 * Lesson content for the Lesson Player screen, including the checkpoint outline.
 *
 * @param id           lesson identifier
 * @param courseId     parent course
 * @param title        lesson title
 * @param body         main body text
 * @param exampleTitle title of the highlighted example box
 * @param example      example body text
 * @param closing      closing paragraph
 * @param durationMin  estimated duration in minutes
 * @param progressPct  player progress percentage (0..100)
 * @param checkpoints  ordered checkpoint outline entries
 */
public record LessonView(UUID id, UUID courseId, String title, String body,
                         String exampleTitle, String example, String closing,
                         Integer durationMin, int progressPct,
                         List<CheckpointView> checkpoints) {

    /**
     * A single checkpoint in the lesson outline.
     *
     * @param label checkpoint label
     * @param state one of {@code done|current|todo}
     */
    public record CheckpointView(String label, String state) {
    }
}
