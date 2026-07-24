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
import com.digishield.tenancy.domain.GroupMember;
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
import com.digishield.tenancy.infrastructure.GroupMemberRepository;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.ArrayList;
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
    private final GroupMemberRepository groupMemberRepository;
    private final JdbcTemplate jdbcTemplate;
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
                              GroupMemberRepository groupMemberRepository,
                              JdbcTemplate jdbcTemplate,
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
        this.groupMemberRepository = groupMemberRepository;
        this.jdbcTemplate = jdbcTemplate;
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
        if (command.name() != null && !command.name().isBlank()) {
            tenant.setName(command.name().trim());
        }
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
        groupRepository.save(group);
        // A smart group is materialised from its rule immediately; a static group
        // starts empty and is populated by adding members.
        int count = isSmart(group) ? materialiseMembers(group) : 0;
        group.setMemberCount(count);
        return toGroupView(groupRepository.save(group));
    }

    @Override
    public MemberCountView evaluateGroup(UUID tenantId, UUID groupId) {
        Group group = requireGroup(tenantId, groupId);
        // Smart groups are recomputed from their rule; static groups just recount.
        int count = isSmart(group) ? materialiseMembers(group) : (int) groupMemberRepository.countByGroupId(groupId);
        group.setMemberCount(count);
        groupRepository.save(group);
        return new MemberCountView(count);
    }

    @Override
    public GroupView updateGroup(UUID tenantId, UUID groupId, GroupView command) {
        Group group = requireGroup(tenantId, groupId);
        if (command.name() != null && !command.name().isBlank()) {
            group.setName(command.name().trim());
        }
        // A present rule replaces the group's rule; an empty rule clears it
        // (turns a smart group into a static one). Omitted -> unchanged.
        if (command.ruleJson() != null) {
            group.setRuleJson(command.ruleJson().isEmpty() ? null : writeJson(command.ruleJson()));
        }
        return toGroupView(groupRepository.save(group));
    }

    @Override
    public void deleteGroup(UUID tenantId, UUID groupId) {
        Group group = requireGroup(tenantId, groupId);
        // group_member rows are removed by the FK ON DELETE CASCADE.
        groupRepository.delete(group);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> listGroupMembers(UUID tenantId, UUID groupId) {
        requireGroup(tenantId, groupId);
        return groupMemberRepository.findUserIdsByGroupId(groupId);
    }

    @Override
    public MemberCountView addGroupMember(UUID tenantId, UUID groupId, UUID userId) {
        Group group = requireGroup(tenantId, groupId);
        Integer inTenant = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app_user WHERE id = ? AND tenant_id = ?",
                Integer.class, userId, tenantId);
        if (inTenant == null || inTenant == 0) {
            throw new IllegalArgumentException("User not found in tenant: " + userId);
        }
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            groupMemberRepository.save(new GroupMember(UUID.randomUUID(), tenantId, groupId, userId));
        }
        return refreshCount(group);
    }

    @Override
    public MemberCountView removeGroupMember(UUID tenantId, UUID groupId, UUID userId) {
        Group group = requireGroup(tenantId, groupId);
        groupMemberRepository.deleteByGroupIdAndUserId(groupId, userId);
        return refreshCount(group);
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
        return new GroupView(g.getId(), g.getName(), readJson(g.getRuleJson()), g.getMemberCount());
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
    /** A group is "smart" when it carries a non-empty membership rule. */
    private boolean isSmart(Group group) {
        Map<String, Object> rule = readJson(group.getRuleJson());
        return rule != null && !rule.isEmpty();
    }

    private Group requireGroup(UUID tenantId, UUID groupId) {
        return groupRepository.findById(groupId)
                .filter(g -> g.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
    }

    /** Recomputes and persists a group's member_count from group_member. */
    private MemberCountView refreshCount(Group group) {
        int count = (int) groupMemberRepository.countByGroupId(group.getId());
        group.setMemberCount(count);
        groupRepository.save(group);
        return new MemberCountView(count);
    }

    /**
     * Replaces a smart group's membership with the tenant users matching its rule
     * (supported keys: {@code risk_score_gte}, {@code department}) and returns the
     * resulting count. Runs raw SQL on the request transaction, so the RLS GUC /
     * role set by the preceding repository call still apply.
     */
    private int materialiseMembers(Group group) {
        Map<String, Object> rule = readJson(group.getRuleJson());
        jdbcTemplate.update("DELETE FROM group_member WHERE group_id = ? AND tenant_id = ?",
                group.getId(), group.getTenantId());
        StringBuilder sql = new StringBuilder(
                "INSERT INTO group_member (id, tenant_id, group_id, user_id) "
                + "SELECT gen_random_uuid(), ?, ?, u.id FROM app_user u WHERE u.tenant_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(group.getTenantId());
        params.add(group.getId());
        params.add(group.getTenantId());
        if (rule != null) {
            if (rule.get("risk_score_gte") instanceof Number n) {
                sql.append(" AND u.risk_score >= ?");
                params.add(n.intValue());
            }
            if (rule.get("department") instanceof String s && !s.isBlank()) {
                sql.append(" AND u.department = ?");
                params.add(s);
            }
        }
        jdbcTemplate.update(sql.toString(), params.toArray());
        return (int) groupMemberRepository.countByGroupId(group.getId());
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
