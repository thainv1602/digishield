package com.digishield.tenancy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A single usage-metering data point for a tenant in a billing period
 * (e.g. number of emails sent in 2026-06).
 */
@Entity
@Table(name = "usage_metering")
public class UsageMetering {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Metric: email_sent | sms_sent | ai_call | storage. */
    @Column(name = "metric", nullable = false)
    private String metric;

    @Column(name = "value", nullable = false)
    private long value;

    /** Billing period, e.g. "2026-06". */
    @Column(name = "period", nullable = false)
    private String period;

    /** Default constructor required by JPA. */
    protected UsageMetering() {
    }

    public UsageMetering(UUID id, UUID tenantId, String metric, long value, String period) {
        this.id = id;
        this.tenantId = tenantId;
        this.metric = metric;
        this.value = value;
        this.period = period;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getMetric() {
        return metric;
    }

    public long getValue() {
        return value;
    }

    public String getPeriod() {
        return period;
    }
}
