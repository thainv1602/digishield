package com.digishield.tenancy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A user group within a tenant. A "smart group" carries a {@code ruleJson}
 * expression (dynamic membership conditions, e.g. {@code risk_score_gte}); a
 * static group simply has an empty/absent rule.
 * <p>
 * The materialised membership count is cached on {@link #memberCount} and
 * recomputed on {@code POST /groups/{id}/evaluate}.
 */
@Entity
@Table(name = "tenant_group")
public class Group {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    /** Smart-group membership rule as a JSON object (text). May be {@code null}. */
    @Column(name = "rule_json", length = 4000)
    private String ruleJson;

    /** Cached number of members after the last evaluation. */
    @Column(name = "member_count")
    private Integer memberCount;

    /** Default constructor required by JPA. */
    protected Group() {
    }

    public Group(UUID id, UUID tenantId, String name, String ruleJson, Integer memberCount) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.ruleJson = ruleJson;
        this.memberCount = memberCount;
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

    public String getRuleJson() {
        return ruleJson;
    }

    public Integer getMemberCount() {
        return memberCount;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRuleJson(String ruleJson) {
        this.ruleJson = ruleJson;
    }

    public void setMemberCount(Integer memberCount) {
        this.memberCount = memberCount;
    }
}
