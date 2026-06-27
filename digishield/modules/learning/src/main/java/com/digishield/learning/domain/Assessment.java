package com.digishield.learning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A knowledge / culture / placement assessment belonging to a tenant.
 * <p>
 * The question set is stored as a JSON document ({@code questions_json}) to keep
 * the schema flexible across the different assessment types.
 */
@Entity
@Table(name = "assessment")
public class Assessment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private AssessmentType type;

    @Column(name = "anonymous", nullable = false)
    private boolean anonymous;

    /** Question set serialized as a JSON document. */
    @Column(name = "questions_json", length = 8000)
    private String questionsJson;

    /** Free-form period label, e.g. "2026-Q2". */
    @Column(name = "period")
    private String period;

    /** Number of submitted responses (used by the aggregated results view). */
    @Column(name = "response_count", nullable = false)
    private int responseCount;

    /** Default constructor required by JPA. */
    protected Assessment() {
    }

    public Assessment(UUID id, UUID tenantId, AssessmentType type, boolean anonymous,
                      String questionsJson, String period, int responseCount) {
        this.id = id;
        this.tenantId = tenantId;
        this.type = type;
        this.anonymous = anonymous;
        this.questionsJson = questionsJson;
        this.period = period;
        this.responseCount = responseCount;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public AssessmentType getType() {
        return type;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    public String getQuestionsJson() {
        return questionsJson;
    }

    public String getPeriod() {
        return period;
    }

    public int getResponseCount() {
        return responseCount;
    }

    public void setResponseCount(int responseCount) {
        this.responseCount = responseCount;
    }
}
