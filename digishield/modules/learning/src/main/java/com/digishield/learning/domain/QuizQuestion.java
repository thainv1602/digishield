package com.digishield.learning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A multiple-choice quiz question (options A–D) attached to a lesson.
 * <p>
 * The four options are stored as discrete columns to keep the schema simple and
 * relational; {@code correctIndex} is 0-based (0=A, 1=B, 2=C, 3=D).
 */
@Entity
@Table(name = "quiz_question")
public class QuizQuestion {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;

    @Column(name = "prompt", nullable = false, length = 1000)
    private String prompt;

    @Column(name = "option_a", nullable = false, length = 1000)
    private String optionA;

    @Column(name = "option_b", nullable = false, length = 1000)
    private String optionB;

    @Column(name = "option_c", nullable = false, length = 1000)
    private String optionC;

    @Column(name = "option_d", nullable = false, length = 1000)
    private String optionD;

    /** 0-based index of the correct option (0=A .. 3=D). */
    @Column(name = "correct_index", nullable = false)
    private int correctIndex;

    @Column(name = "explanation", length = 1000)
    private String explanation;

    @Column(name = "sort_order")
    private Integer sortOrder;

    /** Default constructor required by JPA. */
    protected QuizQuestion() {
    }

    public QuizQuestion(UUID id, UUID tenantId, UUID lessonId, String prompt,
                        String optionA, String optionB, String optionC, String optionD,
                        int correctIndex, String explanation, Integer sortOrder) {
        this.id = id;
        this.tenantId = tenantId;
        this.lessonId = lessonId;
        this.prompt = prompt;
        this.optionA = optionA;
        this.optionB = optionB;
        this.optionC = optionC;
        this.optionD = optionD;
        this.correctIndex = correctIndex;
        this.explanation = explanation;
        this.sortOrder = sortOrder;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getLessonId() {
        return lessonId;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getOptionA() {
        return optionA;
    }

    public String getOptionB() {
        return optionB;
    }

    public String getOptionC() {
        return optionC;
    }

    public String getOptionD() {
        return optionD;
    }

    public int getCorrectIndex() {
        return correctIndex;
    }

    public String getExplanation() {
        return explanation;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }
}
