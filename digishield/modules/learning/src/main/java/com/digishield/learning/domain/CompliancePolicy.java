package com.digishield.learning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A mandatory-training / compliance policy tracked for a tenant.
 */
@Entity
@Table(name = "compliance_policy")
public class CompliancePolicy {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    /** Mapped compliance framework, e.g. ISO27001 / PDPA. */
    @Column(name = "framework")
    private String framework;

    /** Human-readable due rule, e.g. "Hạn: 31/12/2026 · Bắt buộc mọi nhân viên". */
    @Column(name = "due_rule")
    private String dueRule;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    /** Completion percentage for this policy (0..100). */
    @Column(name = "completion_pct", nullable = false)
    private int completionPct;

    /** Default constructor required by JPA. */
    protected CompliancePolicy() {
    }

    public CompliancePolicy(UUID id, UUID tenantId, String name, String framework,
                            String dueRule, boolean mandatory, int completionPct) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.framework = framework;
        this.dueRule = dueRule;
        this.mandatory = mandatory;
        this.completionPct = completionPct;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getFramework() {
        return framework;
    }

    public String getDueRule() {
        return dueRule;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public int getCompletionPct() {
        return completionPct;
    }
}
