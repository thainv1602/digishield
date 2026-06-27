package com.digishield.tenancy.web;

import com.digishield.tenancy.api.AuditLogView;
import com.digishield.tenancy.api.CreateTenantCommand;
import com.digishield.tenancy.api.ScimConfigView;
import com.digishield.tenancy.api.TenancyService;
import com.digishield.tenancy.api.TenantView;
import com.digishield.shared.tenantcontext.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
     * Returns the feature flags of a tenant.
     */
    @GetMapping("/api/v1/tenants/{tenantId}/feature-flags")
    ResponseEntity<?> featureFlags(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(tenancyService.getFeatureFlags(tenantId));
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
