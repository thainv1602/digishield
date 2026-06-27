package com.digishield.tenancy.web;

import com.digishield.tenancy.api.AuditLogView;
import com.digishield.tenancy.api.ChangePlanCommand;
import com.digishield.tenancy.api.CreateTenantCommand;
import com.digishield.tenancy.api.FeatureFlagView;
import com.digishield.tenancy.api.GroupView;
import com.digishield.tenancy.api.MemberCountView;
import com.digishield.tenancy.api.PlanView;
import com.digishield.tenancy.api.ScimConfigView;
import com.digishield.tenancy.api.SubscriptionView;
import com.digishield.tenancy.api.TenancyService;
import com.digishield.tenancy.api.TenantSettingsView;
import com.digishield.tenancy.api.TenantView;
import com.digishield.tenancy.api.UpdateTenantCommand;
import com.digishield.tenancy.api.UsageMeteringView;
import com.digishield.shared.tenantcontext.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for the Tenancy module: tenants (Super Admin), tenant SCIM/SSO
 * settings and the audit log.
 */
@RestController
class TenantController {

    private final TenancyService tenancyService;

    TenantController(TenancyService tenancyService) {
        this.tenancyService = tenancyService;
    }

    /**
     * Lists all tenants for the Super Tenant Console.
     */
    @GetMapping("/api/v1/tenants")
    ResponseEntity<List<TenantView>> tenants() {
        return ResponseEntity.ok(tenancyService.listTenants());
    }

    /**
     * Create a new tenant.
     */
    @PostMapping("/api/v1/tenants")
    ResponseEntity<TenantView> create(@RequestBody CreateTenantCommand command) {
        TenantView created = tenancyService.createTenant(command);
        return ResponseEntity
                .created(URI.create("/api/v1/tenants/" + created.id()))
                .body(created);
    }

    /**
     * Returns a single tenant by id.
     */
    @GetMapping("/api/v1/tenants/{tenantId}")
    ResponseEntity<TenantView> tenant(@PathVariable UUID tenantId) {
        TenantView view = tenancyService.getTenant(tenantId);
        return view != null ? ResponseEntity.ok(view) : ResponseEntity.notFound().build();
    }

    /**
     * Updates a tenant's tier, status and/or data region.
     */
    @PatchMapping("/api/v1/tenants/{tenantId}")
    ResponseEntity<TenantView> updateTenant(@PathVariable UUID tenantId,
                                            @RequestBody UpdateTenantCommand command) {
        return ResponseEntity.ok(tenancyService.updateTenant(tenantId, command));
    }

    /**
     * Updates the configuration (branding/policy/locale) of a tenant.
     */
    @PatchMapping("/api/v1/tenants/{tenantId}/settings")
    ResponseEntity<TenantSettingsView> updateSettings(@PathVariable UUID tenantId,
                                                      @RequestBody TenantSettingsView command) {
        return ResponseEntity.ok(tenancyService.updateTenantSettings(tenantId, command));
    }

    /**
     * Returns the feature flags of a tenant.
     */
    @GetMapping("/api/v1/tenants/{tenantId}/feature-flags")
    ResponseEntity<?> featureFlags(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(tenancyService.getFeatureFlags(tenantId));
    }

    /**
     * Enables or disables a single feature flag for a tenant.
     */
    @PatchMapping("/api/v1/tenants/{tenantId}/feature-flags")
    ResponseEntity<FeatureFlagView> updateFeatureFlag(@PathVariable UUID tenantId,
                                                      @RequestBody FeatureFlagView command) {
        return ResponseEntity.ok(
                tenancyService.setFeatureFlag(tenantId, command.key(), command.enabled()));
    }

    /**
     * Lists groups (including smart groups) of the current tenant.
     */
    @GetMapping("/api/v1/groups")
    ResponseEntity<List<GroupView>> groups() {
        UUID tenantId = TenantContext.requireUuid();
        return ResponseEntity.ok(tenancyService.listGroups(tenantId));
    }

    /**
     * Creates a group (with optional {@code rule_json} for a smart group).
     */
    @PostMapping("/api/v1/groups")
    ResponseEntity<GroupView> createGroup(@RequestBody GroupView command) {
        UUID tenantId = TenantContext.requireUuid();
        GroupView created = tenancyService.createGroup(tenantId, command);
        return ResponseEntity
                .created(URI.create("/api/v1/groups/" + created.id()))
                .body(created);
    }

    /**
     * Re-evaluates a smart group's membership and returns the member count.
     */
    @PostMapping("/api/v1/groups/{groupId}/evaluate")
    ResponseEntity<MemberCountView> evaluateGroup(@PathVariable UUID groupId) {
        UUID tenantId = TenantContext.requireUuid();
        return ResponseEntity.ok(tenancyService.evaluateGroup(tenantId, groupId));
    }

    /**
     * Returns the usage-metering data of a tenant (optionally filtered by period).
     */
    @GetMapping("/api/v1/tenants/{tenantId}/usage")
    ResponseEntity<List<UsageMeteringView>> usage(@PathVariable UUID tenantId,
                                                  @RequestParam(name = "period", required = false)
                                                  String period) {
        return ResponseEntity.ok(tenancyService.getUsage(tenantId, period));
    }

    /**
     * Returns the current subscription of a tenant.
     */
    @GetMapping("/api/v1/tenants/{tenantId}/subscription")
    ResponseEntity<SubscriptionView> subscription(@PathVariable UUID tenantId) {
        SubscriptionView view = tenancyService.getSubscription(tenantId);
        return view != null ? ResponseEntity.ok(view) : ResponseEntity.notFound().build();
    }

    /**
     * Changes a tenant's subscription plan.
     */
    @PutMapping("/api/v1/tenants/{tenantId}/subscription")
    ResponseEntity<SubscriptionView> changeSubscription(@PathVariable UUID tenantId,
                                                        @RequestBody ChangePlanCommand command) {
        return ResponseEntity.ok(tenancyService.changeSubscription(tenantId, command.planId()));
    }

    /**
     * Lists the global service plans.
     */
    @GetMapping("/api/v1/plans")
    ResponseEntity<List<PlanView>> plans() {
        return ResponseEntity.ok(tenancyService.listPlans());
    }

    /**
     * Returns the SCIM / SSO settings of a tenant (connected IdP status).
     */
    @GetMapping("/api/v1/tenants/{tenantId}/settings")
    ResponseEntity<ScimConfigView> settings(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(tenancyService.getScimConfig(tenantId));
    }

    /**
     * Returns the SCIM / SSO settings of the current tenant (Super Admin SCIM screen).
     */
    @GetMapping("/api/v1/super/scim")
    ResponseEntity<ScimConfigView> superScim() {
        UUID tenantId = TenantContext.requireUuid();
        return ResponseEntity.ok(tenancyService.getScimConfig(tenantId));
    }

    /**
     * Returns the audit-log entries of the current tenant.
     */
    @GetMapping({"/api/v1/audit", "/api/v1/super/audit"})
    ResponseEntity<List<AuditLogView>> audit() {
        UUID tenantId = TenantContext.requireUuid();
        return ResponseEntity.ok(tenancyService.listAuditLogs(tenantId));
    }
}
