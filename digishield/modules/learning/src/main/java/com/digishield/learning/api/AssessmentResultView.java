package com.digishield.learning.api;

import java.util.List;

/**
 * Result of submitting quiz answers (Quiz Results screen).
 *
 * @param score   number of correct answers
 * @param total   total number of questions
 * @param passed  whether the pass threshold (>=70%) was reached
 * @param review  per-question correctness + explanation
 */
public record AssessmentResultView(int score, int total, boolean passed,
                                   List<ReviewRow> review) {

    /**
     * A single answer-review row.
     *
     * @param num     1-based question number
     * @param correct whether the submitted answer was correct
     * @param explain explanation (shown for incorrect answers)
     */
    public record ReviewRow(int num, boolean correct, String explain) {
    }
}
