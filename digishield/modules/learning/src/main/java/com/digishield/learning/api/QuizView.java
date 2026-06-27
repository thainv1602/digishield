package com.digishield.learning.api;

import java.util.List;
import java.util.UUID;

/**
 * Quiz payload for the Quiz screen — questions with A–D options and the index of
 * the correct option (the FE computes the score client-side as well as via the
 * server submit endpoint).
 *
 * @param lessonId  lesson the quiz belongs to
 * @param questions ordered quiz questions
 */
public record QuizView(UUID lessonId, List<QuizQuestionView> questions) {

    /**
     * A single quiz question.
     *
     * @param id      question identifier
     * @param q       prompt text
     * @param options the four options in A,B,C,D order
     * @param correct 0-based index of the correct option
     * @param explain explanation shown in the results review
     */
    public record QuizQuestionView(UUID id, String q, List<String> options,
                                   int correct, String explain) {
    }
}
