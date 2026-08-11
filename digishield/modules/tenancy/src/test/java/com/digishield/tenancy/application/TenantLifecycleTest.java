package com.digishield.tenancy.application;

import com.digishield.tenancy.api.TenantSettingsView;
import com.digishield.tenancy.api.TenantView;
import com.digishield.tenancy.api.UpdateTenantCommand;
import com.digishield.tenancy.domain.AuditLog;
import com.digishield.tenancy.domain.Plan;
import com.digishield.tenancy.domain.ScimConfig;
import com.digishield.tenancy.domain.Subscription;
import com.digishield.tenancy.domain.Tenant;
import com.digishield.tenancy.domain.TenantSettings;
import com.digishield.tenancy.domain.TenantStatus;
import com.digishield.tenancy.domain.TenantTier;
import com.digishield.tenancy.domain.UsageMetering;
import com.digishield.tenancy.infrastructure.AuditLogRepository;
import com.digishield.tenancy.infrastructure.PlanRepository;
import com.digishield.tenancy.infrastructure.ScimConfigRepository;
import com.digishield.tenancy.infrastructure.SubscriptionRepository;
import com.digishield.tenancy.infrastructure.TenantRepository;
import com.digishield.tenancy.infrastructure.TenantSettingsRepository;
import com.digishield.tenancy.infrastructure.UsageMeteringRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A tenant's lifecycle: reading it, changing it, and what it is entitled to.
 *
 * <p>Suspending an organisation decides whether hundreds of people can use the
 * product at all, so the interesting assertions here are about what gets
 * recorded and what a partial update leaves alone.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantLifecycleTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private com.digishield.tenancy.infrastructure.FeatureFlagRepository featureFlagRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ScimConfigRepository scimConfigRepository;

    @Mock
    private TenantSettingsRepository tenantSettingsRepository;

    @Mock
    private com.digishield.tenancy.infrastructure.BusinessThresholdsRepository thresholdsRepository;

    @Mock
    private com.digishield.tenancy.infrastructure.GroupRepository groupRepository;

    @Mock
    private com.digishield.tenancy.infrastructure.GroupMemberRepository groupMemberRepository;

    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UsageMeteringRepository usageMeteringRepository;

    @Mock
    private org.springframework.beans.factory.ObjectProvider<
            com.digishield.tenancy.api.TenantAdminProvisioner> adminProvisioner;

    private TenancyServiceImpl service;

    @Captor
    private ArgumentCaptor<AuditLog> auditCaptor;

    @BeforeEach
    void setUp() {
        // Tenant administration runs in PlatformScope, which only opens for a
        // SUPER_ADMIN — the same condition the endpoints carry.
        TestingAuthenticationToken auth = new TestingAuthenticationToken(
                "super", null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
        service = new TenancyServiceImpl(tenantRepository, featureFlagRepository,
                auditLogRepository, scimConfigRepository, tenantSettingsRepository,
                thresholdsRepository, groupRepository, groupMemberRepository, jdbcTemplate,
                planRepository, subscriptionRepository, usageMeteringRepository,
                new ObjectMapper(), adminProvisioner);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Tenant tenant(TenantStatus status) {
        return new Tenant(TENANT, TENANT, "Cơ quan ABC", TenantTier.POOL, "vn", status);
    }

    @Test
    @DisplayName("an update changes only what it names")
    void aPartialUpdateLeavesTheRestAlone() {
        Tenant existing = tenant(TenantStatus.ACTIVE);
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(existing));

        service.updateTenant(TENANT, new UpdateTenantCommand("  Cơ quan XYZ  ", null, null, null));

        assertThat(existing.getName()).isEqualTo("Cơ quan XYZ");
        assertThat(existing.getTier()).isEqualTo(TenantTier.POOL);
        assertThat(existing.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(existing.getDataRegion()).isEqualTo("vn");
    }

    @Test
    @DisplayName("suspending a tenant is recorded as critical in that tenant's own log")
    void aStatusChangeIsAudited() {
        Tenant existing = tenant(TenantStatus.ACTIVE);
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(existing));

        service.updateTenant(TENANT, new UpdateTenantCommand(null, null, "suspended", null));

        assertThat(existing.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog entry = auditCaptor.getValue();
        assertThat(entry.getAction()).isEqualTo("tenant.status_change");
        assertThat(entry.getSeverity()).isEqualTo("critical");
        assertThat(entry.getTarget()).contains("ACTIVE->SUSPENDED");
    }

    @Test
    @DisplayName("a rename is not a status change, so nothing is filed")
    void anUnchangedStatusIsNotAudited() {
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant(TenantStatus.ACTIVE)));

        service.updateTenant(TENANT, new UpdateTenantCommand("Tên mới", null, "active", null));

        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("the retired wire value \"offboarding\" still maps to DEACTIVATED")
    void theOldStatusSpellingIsAccepted() {
        Tenant existing = tenant(TenantStatus.ACTIVE);
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(existing));

        // The spec now says "deactivated", but clients built against the old
        // vocabulary must not silently fail to suspend an organisation.
        service.updateTenant(TENANT, new UpdateTenantCommand(null, null, "offboarding", null));

        assertThat(existing.getStatus()).isEqualTo(TenantStatus.DEACTIVATED);
    }

    @Test
    void updatingATenantThatDoesNotExistIsRefused() {
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateTenant(
                TENANT, new UpdateTenantCommand("x", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aTenantIsReadBackWithItsTierAndRegion() {
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(tenant(TenantStatus.ACTIVE)));

        TenantView view = service.getTenant(TENANT);

        assertThat(view.name()).isEqualTo("Cơ quan ABC");
        assertThat(view.dataRegion()).isEqualTo("vn");
        // Lower case, as the spec declares and as every other DTO emits. These
        // used to be POOL and ACTIVE, which is why the super-admin console —
        // comparing against "active" — counted no live tenants at all.
        assertThat(view.tier()).isEqualTo("pool");
        assertThat(view.status()).isEqualTo("active");
    }

    @Test
    void listingTenantsMapsEachOne() {
        when(tenantRepository.findAll()).thenReturn(List.of(
                tenant(TenantStatus.ACTIVE),
                new Tenant(UUID.randomUUID(), UUID.randomUUID(), "Cơ quan DEF",
                        TenantTier.SILO, "vn-hn", TenantStatus.SUSPENDED)));

        assertThat(service.listTenants()).extracting(TenantView::name)
                .containsExactly("Cơ quan ABC", "Cơ quan DEF");
    }

    @Test
    @DisplayName("settings are created on first read rather than returning nothing")
    void settingsDefaultOnFirstRead() {
        when(tenantSettingsRepository.findByTenantId(TENANT)).thenReturn(Optional.empty());
        when(tenantSettingsRepository.save(any(TenantSettings.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TenantSettingsView view = service.getTenantSettings(TENANT);

        assertThat(view).isNotNull();
        assertThat(view.tenantId()).isEqualTo(TENANT);
    }

    @Test
    void settingsAreUpdatedInPlace() {
        TenantSettings existing = new TenantSettings(
                UUID.randomUUID(), TENANT, null, null, "vi");
        when(tenantSettingsRepository.findByTenantId(TENANT)).thenReturn(Optional.of(existing));
        when(tenantSettingsRepository.save(any(TenantSettings.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TenantSettingsView updated = service.updateTenantSettings(
                TENANT, new TenantSettingsView(TENANT, null, null, "en"));

        assertThat(updated.defaultLocale()).isEqualTo("en");
    }

    @Test
    @DisplayName("a tenant with no SCIM configuration reads as null, not as a disconnected view")
    void scimIsNullWhenUnconfigured() {
        when(scimConfigRepository.findByTenantId(TENANT)).thenReturn(Optional.empty());

        // GET /tenants/{id}/settings then answers 200 with an empty body, while
        // the spec declares a ScimConfig object. Pinned so the difference is
        // visible; a client reading .connected() on it gets a null pointer.
        assertThat(service.getScimConfig(TENANT)).isNull();
    }

    @Test
    void scimConfigurationIsMapped() {
        when(scimConfigRepository.findByTenantId(TENANT)).thenReturn(Optional.of(
                new ScimConfig(UUID.randomUUID(), TENANT, "Entra ID", true,
                        "idp-tenant", "client-1", "https://scim.example/v2", null, 42, 0)));

        var view = service.getScimConfig(TENANT);

        assertThat(view.idpName()).isEqualTo("Entra ID");
        assertThat(view.connected()).isTrue();
        assertThat(view.syncedUserCount()).isEqualTo(42);
    }

    @Test
    @DisplayName("changing plan reuses the existing subscription rather than opening a second")
    void changingPlanUpdatesTheSubscription() {
        UUID newPlan = UUID.randomUUID();
        Subscription existing = new Subscription(UUID.randomUUID(), TENANT, UUID.randomUUID(),
                "past_due", LocalDate.now().plusMonths(2));
        when(subscriptionRepository.findByTenantId(TENANT)).thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var view = service.changeSubscription(TENANT, newPlan);

        assertThat(existing.getPlanId()).isEqualTo(newPlan);
        // Paying again clears the arrears state; the renewal date is left alone.
        assertThat(view.status()).isEqualTo("active");
        assertThat(existing.getRenewsAt()).isEqualTo(LocalDate.now().plusMonths(2));
    }

    @Test
    @DisplayName("a tenant with no subscription gets one, renewing in a year")
    void aFirstSubscriptionIsCreated() {
        UUID plan = UUID.randomUUID();
        when(subscriptionRepository.findByTenantId(TENANT)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var view = service.changeSubscription(TENANT, plan);

        assertThat(view.planId()).isEqualTo(plan);
        assertThat(view.renewsAt()).isEqualTo(LocalDate.now().plusYears(1));
    }

    @Test
    void usageIsReadForThePeriodAsked() {
        when(usageMeteringRepository.findByTenantIdAndPeriod(TENANT, "2026-08")).thenReturn(List.of(
                new UsageMetering(UUID.randomUUID(), TENANT, "email_sent", 1200, "2026-08")));

        assertThat(service.getUsage(TENANT, "2026-08"))
                .singleElement()
                .satisfies(u -> {
                    assertThat(u.metric()).isEqualTo("email_sent");
                    assertThat(u.value()).isEqualTo(1200);
                });
    }

    @Test
    void plansAreListed() {
        when(planRepository.findAll()).thenReturn(List.of(
                new Plan(UUID.randomUUID(), "gov", "{}", "{}")));

        assertThat(service.listPlans()).singleElement()
                .satisfies(p -> assertThat(p.name()).isEqualTo("gov"));
    }
}
