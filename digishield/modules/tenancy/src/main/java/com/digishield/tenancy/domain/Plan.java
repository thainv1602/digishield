package com.digishield.tenancy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A service plan in the global billing catalogue (edu | business | gov).
 * <p>
 * Plans are global rows (not tenant-scoped): every tenant chooses one of them
 * for its {@link Subscription}. {@code limitsJson} and {@code featuresJson} hold
 * JSON objects (seats, emails, ai_calls, feature toggles).
 */
@Entity
@Table(name = "plan")
public class Plan {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Plan name: edu | business | gov. */
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "limits_json", length = 4000)
    private String limitsJson;

    @Column(name = "features_json", length = 4000)
    private String featuresJson;

    /** Default constructor required by JPA. */
    protected Plan() {
    }

    public Plan(UUID id, String name, String limitsJson, String featuresJson) {
        this.id = id;
        this.name = name;
        this.limitsJson = limitsJson;
        this.featuresJson = featuresJson;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLimitsJson() {
        return limitsJson;
    }

    public String getFeaturesJson() {
        return featuresJson;
    }
}
