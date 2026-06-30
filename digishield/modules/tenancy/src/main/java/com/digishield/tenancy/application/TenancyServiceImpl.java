package com.digishield.tenancy.application;

import com.digishield.tenancy.api.AuditLogView;
import com.digishield.tenancy.api.BusinessThresholdsView;
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
import com.digishield.tenancy.domain.AuditLog;
import com.digishield.tenancy.domain.BusinessThresholds;
import com.digishield.tenancy.domain.FeatureFlag;
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
import com.digishield.tenancy.infrastructure.BusinessThresholdsRepository;
import com.digishield.tenancy.infrastructure.FeatureFlagRepository;
import com.digishield.tenancy.infrastructure.GroupRepository;
import com.digishield.tenancy.infrastructure.PlanRepository;
import com.digishield.tenancy.infrastructure.ScimConfigRepository;
import com.digishield.tenancy.infrastructure.SubscriptionRepository;
import com.digishield.tenancy.infrastructure.TenantRepository;
import com.digishield.tenancy.infrastructure.TenantSettingsRepository;
import com.digishield.tenancy.infrastructure.UsageMeteringRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of {@link TenancyService}.
 */
@Service
@Transactional
public class TenancyServiceImpl implements TenancyService {

    private static final TypeReference<Map<String, Object>> JSON_MAP =
            new TypeReference<>() {
            };

    private final TenantRepository tenantRepository;
    private final FeatureFlagRepository featureFlagRepository;
    private final AuditLogRepository auditLogRepository;
    private final ScimConfigRepository scimConfigRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final BusinessThresholdsRepository businessThresholdsRepository;
    private final GroupRepository groupRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UsageMeteringRepository usageMeteringRepository;
    private final ObjectMapper objectMapper;

    public TenancyServiceImpl(TenantRepository tenantRepository,
                              FeatureFlagRepository featureFlagRepository,
                              AuditLogRepository auditLogRepository,
                              ScimConfigRepository scimConfigRepository,
                              TenantSettingsRepository tenantSettingsRepository,
                              BusinessThresholdsRepository businessThresholdsRepository,
                              GroupRepository groupRepository,
                              PlanRepository planRepository,
                              SubscriptionRepository subscriptionRepository,
                              UsageMeteringRepository usageMeteringRepository,
                              ObjectMapper objectMapper) {
        this.tenantRepository = tenantRepository;
        this.featureFlagRepository = featureFlagRepository;
        this.auditLogRepository = auditLogRepository;
        this.scimConfigRepository = scimConfigRepository;
        this.tenantSettingsRepository = tenantSettingsRepository;
        this.businessThresholdsRepository = businessThresholdsRepository;
        this.groupRepository = groupRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.usageMeteringRepository = usageMeteringRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public TenantView createTenant(CreateTenantCommand command) {
        UUID id = UUID.randomUUID();
        Tenant tenant = new Tenant(
                id,
                id,
                command.name(),
                TenantTier.valueOf(command.tier()),
                command.dataRegion(),
                TenantStatus.PROVISIONING
        );
        Tenant saved = tenantRepository.save(tenant);
        return toView(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantView> listTenants() {
        return tenantRepository.findAll().stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogView> listAuditLogs(UUID tenantId) {
        return auditLogRepository.findByTenantIdOrderByTsDesc(tenantId).stream()
                .map(this::toAuditView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ScimConfigView getScimConfig(UUID tenantId) {
        return scimConfigRepository.findByTenantId(tenantId)
                .map(this::toScimView)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeatureFlagView> getFeatureFlags(UUID tenantId) {
        return featureFlagRepository.findByTenantId(tenantId).stream()
                .map(f -> new FeatureFlagView(f.getKey(), f.isEnabled()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEnabled(UUID tenantId, String key) {
        return featureFlagRepository.findByTenantIdAndKey(tenantId, key)
                .map(f -> f.isEnabled())
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public TenantView getTenant(UUID id) {
        return tenantRepository.findById(id)
                .map(this::toView)
                .orElse(null);
    }

    @Override
    public TenantView updateTenant(UUID id, UpdateTenantCommand command) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + id));
        if (command.tier() != null && !command.tier().isBlank()) {
            tenant.setTier(TenantTier.valueOf(command.tier().trim().toUpperCase()));
        }
        if (command.status() != null && !command.status().isBlank()) {
            tenant.setStatus(parseStatus(command.status()));
        }
        if (command.dataRegion() != null && !command.dataRegion().isBlank()) {
            tenant.setDataRegion(command.dataRegion().trim());
        }
        return toView(tenantRepository.save(tenant));
    }

    @Override
    public TenantSettingsView getTenantSettings(UUID tenantId) {
        TenantSettings settings = tenantSettingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> tenantSettingsRepository.save(
                        new TenantSettings(UUID.randomUUID(), tenantId, null, null, "vi")));
        return toSettingsView(settings);
    }

    @Override
    public TenantSettingsView updateTenantSettings(UUID tenantId, TenantSettingsView command) {
        TenantSettings settings = tenantSettingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> new TenantSettings(UUID.randomUUID(), tenantId, null, null, "vi"));
        if (command.branding() != null) {
            settings.setBrandingJson(writeJson(command.branding()));
        }
        if (command.policy() != null) {
            settings.setPolicyJson(writeJson(command.policy()));
        }
        if (command.defaultLocale() != null && !command.defaultLocale().isBlank()) {
            settings.setDefaultLocale(command.defaultLocale().trim());
        }
        return toSettingsView(tenantSettingsRepository.save(settings));
    }

    @Override
    public BusinessThresholdsView getThresholds(UUID tenantId) {
        BusinessThresholds t = businessThresholdsRepository.findByTenantId(tenantId)
                .orElseGet(() -> businessThresholdsRepository.save(
                        new BusinessThresholds(UUID.randomUUID(), tenantId, 60, 70, 2)));
        return toThresholdsView(t);
    }

    @Override
    public BusinessThresholdsView updateThresholds(UUID tenantId, BusinessThresholdsView command) {
        BusinessThresholds t = businessThresholdsRepository.findByTenantId(tenantId)
                .orElseGet(() -> new BusinessThresholds(UUID.randomUUID(), tenantId, 60, 70, 2));
        if (command.riskAlertScore() != null) {
            t.setRiskAlertScore(clamp(command.riskAlertScore(), 0, 100));
        }
        if (command.passScorePct() != null) {
            t.setPassScorePct(clamp(command.passScorePct(), 0, 100));
        }
        if (command.minCampaignsPerQuarter() != null) {
            t.setMinCampaignsPerQuarter(clamp(command.minCampaignsPerQuarter(), 0, 100));
        }
        return toThresholdsView(businessThresholdsRepository.save(t));
    }

    private BusinessThresholdsView toThresholdsView(BusinessThresholds t) {
        return new BusinessThresholdsView(
                t.getRiskAlertScore(), t.getPassScorePct(), t.getMinCampaignsPerQuarter());
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public FeatureFlagView setFeatureFlag(UUID tenantId, String key, boolean enabled) {
        FeatureFlag flag = featureFlagRepository.findByTenantIdAndKey(tenantId, key)
                .orElseGet(() -> new FeatureFlag(UUID.randomUUID(), tenantId, key, enabled));
        flag.setEnabled(enabled);
        FeatureFlag saved = featureFlagRepository.save(flag);
        return new FeatureFlagView(saved.getKey(), saved.isEnabled());
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupView> listGroups(UUID tenantId) {
        return groupRepository.findByTenantIdOrderByName(tenantId).stream()
                .map(this::toGroupView)
                .toList();
    }

    @Override
    public GroupView createGroup(UUID tenantId, GroupView command) {
        Group group = new Group(
                UUID.randomUUID(),
                tenantId,
                command.name(),
                command.ruleJson() != null ? writeJson(command.ruleJson()) : null,
                0);
        // A fresh smart group gets an initial materialised count.
        group.setMemberCount(evaluateMembers(group));
        return toGroupView(groupRepository.save(group));
    }

    @Override
    public MemberCountView evaluateGroup(UUID tenantId, UUID groupId) {
        Group group = groupRepository.findById(groupId)
                .filter(g -> g.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
        int count = evaluateMembers(group);
        group.setMemberCount(count);
        groupRepository.save(group);
        return new MemberCountView(count);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsageMeteringView> getUsage(UUID tenantId, String period) {
        List<UsageMetering> rows = (period != null && !period.isBlank())
                ? usageMeteringRepository.findByTenantIdAndPeriod(tenantId, period.trim())
                : usageMeteringRepository.findByTenantId(tenantId);
        return rows.stream().map(this::toUsageView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionView getSubscription(UUID tenantId) {
        return subscriptionRepository.findByTenantId(tenantId)
                .map(this::toSubscriptionView)
                .orElse(null);
    }

    @Override
    public SubscriptionView changeSubscription(UUID tenantId, UUID planId) {
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseGet(() -> new Subscription(UUID.randomUUID(), tenantId, planId,
                        "active", LocalDate.now().plusYears(1)));
        subscription.setPlanId(planId);
        subscription.setStatus("active");
        if (subscription.getRenewsAt() == null) {
            subscription.setRenewsAt(LocalDate.now().plusYears(1));
        }
        return toSubscriptionView(subscriptionRepository.save(subscription));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanView> listPlans() {
        return planRepository.findAll().stream()
                .map(this::toPlanView)
                .toList();
    }

    // ------------------------------------------------------------------
    // Mapping helpers
    // ------------------------------------------------------------------

    private TenantView toView(Tenant tenant) {
        return new TenantView(
                tenant.getId(),
                tenant.getTenantId(),
                tenant.getName(),
                tenant.getTier() != null ? tenant.getTier().name() : null,
                tenant.getDataRegion(),
                tenant.getStatus() != null ? tenant.getStatus().name() : null,
                tenant.getUserCount(),
                tenant.getDomain()
        );
    }

    private AuditLogView toAuditView(AuditLog a) {
        return new AuditLogView(a.getId(), a.getTs(), a.getActor(), a.getAction(),
                a.getTarget(), a.getIp(), a.getSeverity());
    }

    private ScimConfigView toScimView(ScimConfig s) {
        String syncStatus;
        if (s.getLastSyncAt() == null) {
            syncStatus = "never";
        } else if (s.getSyncErrorCount() != null && s.getSyncErrorCount() > 0) {
            syncStatus = "error";
        } else {
            syncStatus = "ok";
        }
        return new ScimConfigView(s.getTenantId(), s.getIdpName(), s.isConnected(),
                s.getIdpTenantId(), s.getClientId(), s.getScimEndpoint(), s.getLastSyncAt(),
                s.getSyncedUserCount(), s.getSyncErrorCount(), syncStatus);
    }

    private TenantSettingsView toSettingsView(TenantSettings s) {
        return new TenantSettingsView(
                s.getTenantId(),
                readJson(s.getBrandingJson()),
                readJson(s.getPolicyJson()),
                s.getDefaultLocale());
    }

    private GroupView toGroupView(Group g) {
        return new GroupView(g.getId(), g.getName(), readJson(g.getRuleJson()));
    }

    private PlanView toPlanView(Plan p) {
        return new PlanView(p.getId(), p.getName(),
                readJson(p.getLimitsJson()), readJson(p.getFeaturesJson()));
    }

    private SubscriptionView toSubscriptionView(Subscription s) {
        return new SubscriptionView(s.getId(), s.getTenantId(), s.getPlanId(),
                s.getStatus(), s.getRenewsAt());
    }

    private UsageMeteringView toUsageView(UsageMetering u) {
        return new UsageMeteringView(u.getTenantId(), u.getMetric(), u.getValue(), u.getPeriod());
    }

    /**
     * Deterministic, stand-in membership evaluation for a smart group: the count
     * is derived from the rule expression so it is stable across calls (no real
     * directory query is performed in this module). A higher {@code risk_score_gte}
     * threshold yields fewer members; a department filter narrows the pool.
     */
    private int evaluateMembers(Group group) {
        Map<String, Object> rule = readJson(group.getRuleJson());
        if (rule == null || rule.isEmpty()) {
            // Static group: keep its existing count, defaulting to 0.
            return group.getMemberCount() != null ? group.getMemberCount() : 0;
        }
        int base = 420;
        Object riskGte = rule.get("risk_score_gte");
        if (riskGte instanceof Number n) {
            // Fewer people exceed a higher risk threshold.
            base = Math.max(3, (int) Math.round(base * (100 - Math.min(100, n.doubleValue())) / 100.0));
        }
        if (rule.containsKey("department") || rule.containsKey("department_id")) {
            base = Math.max(1, base / 5);
        }
        // Stable jitter from the rule contents so repeated evaluations match.
        int jitter = Math.floorMod(group.getRuleJson() != null ? group.getRuleJson().hashCode() : 0, 17);
        return base + jitter;
    }

    private TenantStatus parseStatus(String raw) {
        String normalized = raw.trim().toUpperCase();
        // The OpenAPI uses "offboarding" where the domain models DEACTIVATED.
        if (normalized.equals("OFFBOARDING")) {
            return TenantStatus.DEACTIVATED;
        }
        return TenantStatus.valueOf(normalized);
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, JSON_MAP);
        } catch (Exception e) {
            return null;
        }
    }

    private String writeJson(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(map));
        } catch (Exception e) {
            return null;
        }
    }
}
