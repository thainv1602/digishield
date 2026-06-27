package com.digishield.tenancy.application;

import com.digishield.shared.tenantcontext.DemoTenants;
import com.digishield.tenancy.domain.AuditLog;
import com.digishield.tenancy.domain.ScimConfig;
import com.digishield.tenancy.domain.Tenant;
import com.digishield.tenancy.domain.TenantStatus;
import com.digishield.tenancy.domain.TenantTier;
import com.digishield.tenancy.infrastructure.AuditLogRepository;
import com.digishield.tenancy.infrastructure.ScimConfigRepository;
import com.digishield.tenancy.infrastructure.TenantRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Seeds demo Tenancy data (tenants for the Super Console, audit-log entries and
 * a SCIM/SSO config) for the {@code dev} profile.
 * <p>
 * The audit log and SCIM config are scoped to the fixed demo tenant
 * {@link DemoTenants#DEMO_TENANT_ID}; tenants themselves are global rows shown
 * in the Super Admin console.
 */
@Component
@Profile("dev")
@Order(20)
class TenancyDevSeeder implements CommandLineRunner {

    private static final UUID TENANT = DemoTenants.DEMO_TENANT_ID;

    private final TenantRepository tenantRepository;
    private final AuditLogRepository auditLogRepository;
    private final ScimConfigRepository scimConfigRepository;

    TenancyDevSeeder(TenantRepository tenantRepository,
                     AuditLogRepository auditLogRepository,
                     ScimConfigRepository scimConfigRepository) {
        this.tenantRepository = tenantRepository;
        this.auditLogRepository = auditLogRepository;
        this.scimConfigRepository = scimConfigRepository;
    }

    @Override
    public void run(String... args) {
        seedTenants();
        seedAuditLogs();
        seedScim();
    }

    private void seedTenants() {
        if (tenantRepository.findById(TENANT).isEmpty()) {
            // The demo tenant itself (so requests scoped to it have a Tenant row).
            tenantRepository.save(new Tenant(TENANT, TENANT, "Cơ quan ABC",
                    TenantTier.SILO, "vn", TenantStatus.ACTIVE, 1240, "abc.gov.vn"));
        }
        if (tenantRepository.count() <= 1) {
            tenantRepository.save(new Tenant(UUID.randomUUID(), UUID.randomUUID(),
                    "Trường ĐH XYZ", TenantTier.BRIDGE, "vn", TenantStatus.ACTIVE, 8500, "dhxyz.edu.vn"));
            tenantRepository.save(new Tenant(UUID.randomUUID(), UUID.randomUUID(),
                    "Công ty DEF", TenantTier.POOL, "cloud", TenantStatus.SUSPENDED, 540, "def.com.vn"));
        }
    }

    private void seedAuditLogs() {
        if (!auditLogRepository.findByTenantIdOrderByTsDesc(TENANT).isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        auditLogRepository.save(new AuditLog(UUID.randomUUID(), TENANT,
                now.minus(5, ChronoUnit.MINUTES), "admin@abc.gov.vn", "broadcast_alert",
                "org:abc", "10.0.0.1", "critical"));
        auditLogRepository.save(new AuditLog(UUID.randomUUID(), TENANT,
                now.minus(22, ChronoUnit.MINUTES), "analyst1@abc.vn", "triage:confirm",
                "report:#4821", "10.0.0.5", "sensitive"));
        auditLogRepository.save(new AuditLog(UUID.randomUUID(), TENANT,
                now.minus(37, ChronoUnit.MINUTES), "admin@abc.gov.vn", "user.role_change",
                "user:#88", "10.0.0.1", "standard"));
        auditLogRepository.save(new AuditLog(UUID.randomUUID(), TENANT,
                now.minus(65, ChronoUnit.MINUTES), "superadmin@ds.vn", "tenant.suspend",
                "tenant:def", "1.2.3.4", "critical"));
        auditLogRepository.save(new AuditLog(UUID.randomUUID(), TENANT,
                now.minus(2, ChronoUnit.HOURS), "analyst1@abc.vn", "blacklist.add",
                "url:bit.ly/vbc-xacminh", "10.0.0.5", "sensitive"));
        auditLogRepository.save(new AuditLog(UUID.randomUUID(), TENANT,
                now.minus(3, ChronoUnit.HOURS), "admin@abc.gov.vn", "user.login",
                "user:#12", "10.0.0.1", "standard"));
    }

    private void seedScim() {
        if (scimConfigRepository.findByTenantId(TENANT).isPresent()) {
            return;
        }
        scimConfigRepository.save(new ScimConfig(UUID.randomUUID(), TENANT,
                "Microsoft Entra ID (Azure AD)", true,
                "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "ds-client-abc-xyz-001",
                "https://api.digishield.vn/scim/v2",
                Instant.parse("2026-06-27T08:00:00Z"), 1240, 0));
    }
}
