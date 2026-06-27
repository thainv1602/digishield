package com.digishield.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * An application user belonging to a specific tenant.
 * <p>
 * Each entity carries a {@code tenantId} to support multi-tenant data isolation.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "email", nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    /** Display name (optional). */
    @Column(name = "name")
    private String name;

    /** Department / org unit label (optional). */
    @Column(name = "department")
    private String department;

    /** Cached risk score 0..100 (optional, surfaced on the Users screen). */
    @Column(name = "risk_score")
    private Integer riskScore;

    /** Default constructor required by JPA. */
    protected AppUser() {
    }

    public AppUser(UUID id, UUID tenantId, String email, Role role, UserStatus status) {
        this(id, tenantId, email, role, status, null, null, null);
    }

    public AppUser(UUID id, UUID tenantId, String email, Role role, UserStatus status,
                   String name, String department, Integer riskScore) {
        this.id = id;
        this.tenantId = tenantId;
        this.email = email;
        this.role = role;
        this.status = status;
        this.name = name;
        this.department = department;
        this.riskScore = riskScore;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getName() {
        return name;
    }

    public String getDepartment() {
        return department;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public void setRiskScore(Integer riskScore) {
        this.riskScore = riskScore;
    }
}
