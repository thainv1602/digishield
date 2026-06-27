package com.digishield.tenancy.application;

import com.digishield.shared.tenantcontext.DemoTenants;
import com.digishield.tenancy.domain.AuditLog;
import com.digishield.tenancy.domain.Group;
import com.digishield.tenancy.domain.Plan;
import com.digishield.tenancy.domain.ScimConfig;
import com.digishield.tenancy.domain.Subscription;
import com.digishield.tenancy.domain.Tenant;
import com.digishield.tenancy.domain.TenantSettings;
import com.digishield.tenancy.domain.TenantStatus;
import com.digishield.tenancy.domain.TenantTier;
import com.digishield.tenancy.domain.UsageMetering;
import com.digishield.tenancy.infrastructure.AuditLogRepository;
import com.digishield.tenancy.infrastructure.GroupRepository;
import com.digishield.tenancy.infrastructure.PlanRepository;
import com.digishield.tenancy.infrastructure.ScimConfigRepository;
import com.digishield.tenancy.infrastructure.SubscriptionRepository;
import com.digishield.tenancy.infrastructure.TenantRepository;
import com.digishield.tenancy.infrastructure.TenantSettingsRepository;
import com.digishield.tenancy.infrastructure.UsageMeteringRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Seeds demo Tenancy data (tenants for the Super Console, audit-log entries, a
 * SCIM/SSO config, tenant settings, smart groups, the billing plan catalogue, a
 * subscription and usage metering) for the {@code dev} profile.
 * <p>
 * All tenant-scoped data is attached to the fixed demo tenant
 * {@link DemoTenants#DEMO_TENANT_ID}; tenants and plans themselves are global
 * rows shown in the Super Admin / Billing consoles.
 */
@Component
@Profile("dev")
@Order(20)
class TenancyDevSeeder implements CommandLineRunner {

    private static final UUID TENANT = DemoTenants.DEMO_TENANT_ID;

    // Stable plan ids so the demo subscription can reference one deterministically.
    private static final UUID PLAN_EDU =
            UUID.fromString("22222222-0000-0000-0000-0000000000ed");
    private static final UUID PLAN_BUSINESS =
            UUID.fromString("22222222-0000-0000-0000-0000000000b5");
    private static final UUID PLAN_GOV =
            UUID.fromString("22222222-0000-0000-0000-0000000000a0");

    private final TenantRepository tenantRepository;
    private final AuditLogRepository auditLogRepository;
    private final ScimConfigRepository scimConfigRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final GroupRepository groupRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UsageMeteringRepository usageMeteringRepository;
    private final ObjectMapper objectMapper;

    TenancyDevSeeder(TenantRepository tenantRepository,
                     AuditLogRepository auditLogRepository,
                     ScimConfigRepository scimConfigRepository,
                     TenantSettingsRepository tenantSettingsRepository,
                     GroupRepository groupRepository,
                     PlanRepository planRepository,
                     SubscriptionRepository subscriptionRepository,
                     UsageMeteringRepository usageMeteringRepository,
                     ObjectMapper objectMapper) {
        this.tenantRepository = tenantRepository;
        this.auditLogRepository = auditLogRepository;
        this.scimConfigRepository = scimConfigRepository;
        this.tenantSettingsRepository = tenantSettingsRepository;
        this.groupRepository = groupRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.usageMeteringRepository = usageMeteringRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) {
        seedTenants();
        seedAuditLogs();
        seedScim();
        seedTenantSettings();
        seedGroups();
        seedPlans();
        seedSubscription();
        seedUsage();
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

    private void seedTenantSettings() {
        if (tenantSettingsRepository.findByTenantId(TENANT).isPresent()) {
            return;
        }
        Map<String, Object> branding = new LinkedHashMap<>();
        branding.put("logo_url", "https://cdn.digishield.vn/abc/logo.svg");
        branding.put("primary_color", "#0B6E4F");
        branding.put("subdomain", "abc");
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("risk_score_threshold", 70);
        policy.put("mandatory_training", true);
        policy.put("phish_prone_target_pct", 5);
        tenantSettingsRepository.save(new TenantSettings(UUID.randomUUID(), TENANT,
                writeJson(branding), writeJson(policy), "vi"));
    }

    private void seedGroups() {
        if (!groupRepository.findByTenantIdOrderByName(TENANT).isEmpty()) {
            return;
        }
        Map<String, Object> highRisk = new LinkedHashMap<>();
        highRisk.put("risk_score_gte", 70);
        Group g1 = new Group(UUID.randomUUID(), TENANT, "Nhân sự rủi ro cao",
                writeJson(highRisk), null);
        g1.setMemberCount(126);

        Map<String, Object> finance = new LinkedHashMap<>();
        finance.put("department", "ke-toan");
        finance.put("risk_score_gte", 50);
        Group g2 = new Group(UUID.randomUUID(), TENANT, "Phòng Kế toán",
                writeJson(finance), null);
        g2.setMemberCount(48);

        // A static group (no rule).
        Group g3 = new Group(UUID.randomUUID(), TENANT, "Ban Lãnh đạo", null, 12);

        groupRepository.save(g1);
        groupRepository.save(g2);
        groupRepository.save(g3);
    }

    private void seedPlans() {
        if (planRepository.count() > 0) {
            return;
        }
        planRepository.save(new Plan(PLAN_EDU, "edu",
                writeJson(limits(2000, 50000, 5000)),
                writeJson(features(false, true, false))));
        planRepository.save(new Plan(PLAN_BUSINESS, "business",
                writeJson(limits(10000, 500000, 50000)),
                writeJson(features(true, true, true))));
        planRepository.save(new Plan(PLAN_GOV, "gov",
                writeJson(limits(50000, 2000000, 200000)),
                writeJson(features(true, true, true))));
    }

    private void seedSubscription() {
        if (subscriptionRepository.findByTenantId(TENANT).isPresent()) {
            return;
        }
        subscriptionRepository.save(new Subscription(UUID.randomUUID(), TENANT, PLAN_GOV,
                "active", LocalDate.parse("2027-01-01")));
    }

    private void seedUsage() {
        if (!usageMeteringRepository.findByTenantId(TENANT).isEmpty()) {
            return;
        }
        String period = "2026-06";
        usageMeteringRepository.save(new UsageMetering(UUID.randomUUID(), TENANT,
                "email_sent", 184200, period));
        usageMeteringRepository.save(new UsageMetering(UUID.randomUUID(), TENANT,
                "sms_sent", 12400, period));
        usageMeteringRepository.save(new UsageMetering(UUID.randomUUID(), TENANT,
                "ai_call", 38900, period));
        usageMeteringRepository.save(new UsageMetering(UUID.randomUUID(), TENANT,
                "storage", 256, period));
    }

    private Map<String, Object> limits(int seats, int emails, int aiCalls) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seats", seats);
        m.put("emails", emails);
        m.put("ai_calls", aiCalls);
        return m;
    }

    private Map<String, Object> features(boolean deepfakeSim, boolean training, boolean soc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deepfake_sim", deepfakeSim);
        m.put("training", training);
        m.put("soc_console", soc);
        return m;
    }

    private String writeJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return null;
        }
    }
}
