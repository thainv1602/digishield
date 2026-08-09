package com.digishield.tenancy.application;

import com.digishield.tenancy.api.BusinessThresholdsView;
import com.digishield.tenancy.api.CreateTenantCommand;
import com.digishield.tenancy.api.FeatureFlagView;
import com.digishield.tenancy.api.TenantView;
import com.digishield.tenancy.domain.BusinessThresholds;
import com.digishield.tenancy.domain.FeatureFlag;
import com.digishield.tenancy.domain.Tenant;
import com.digishield.tenancy.domain.TenantStatus;
import com.digishield.tenancy.domain.TenantTier;
import com.digishield.tenancy.infrastructure.BusinessThresholdsRepository;
import com.digishield.tenancy.infrastructure.FeatureFlagRepository;
import com.digishield.tenancy.infrastructure.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TenancyServiceImpl}.
 * <p>
 * Pure Mockito unit tests: no Spring context, no real database.
 */
@ExtendWith(MockitoExtension.class)
class TenancyServiceImplTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private org.springframework.beans.factory.ObjectProvider<com.digishield.tenancy.api.TenantAdminProvisioner> adminProvisioner;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private FeatureFlagRepository featureFlagRepository;

    @Mock
    private BusinessThresholdsRepository businessThresholdsRepository;

    @InjectMocks
    private TenancyServiceImpl tenancyService;

    @Captor
    private ArgumentCaptor<Tenant> tenantCaptor;

    /**
     * Creating a tenant writes a row whose tenant_id is not the caller's, so it
     * runs in {@code PlatformScope} — which only opens for a SUPER_ADMIN. The
     * endpoint is SUPER_ADMIN-only anyway; the context makes that explicit here.
     */
    private static void authenticateAsSuperAdmin() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(
                "super", null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createTenantWithAnAdminEmailProvisionsThatAdminInTheNewTenant() {
        authenticateAsSuperAdmin();
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        com.digishield.tenancy.api.TenantAdminProvisioner provisioner =
                org.mockito.Mockito.mock(com.digishield.tenancy.api.TenantAdminProvisioner.class);
        when(adminProvisioner.getIfAvailable()).thenReturn(provisioner);

        TenantView view = tenancyService.createTenant(
                new CreateTenantCommand("Acme Corp", "SILO", "eu-west-1", " boss@acme.vn "));

        // The admin belongs to the tenant just created, not the caller's, and the
        // address is trimmed before it becomes a login.
        verify(provisioner).provisionAdmin(view.id(), "boss@acme.vn");
    }

    @Test
    void createTenantWhenTheAdminCannotBeCreatedTakesTheTenantWithIt() {
        authenticateAsSuperAdmin();
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        com.digishield.tenancy.api.TenantAdminProvisioner provisioner =
                org.mockito.Mockito.mock(com.digishield.tenancy.api.TenantAdminProvisioner.class);
        when(adminProvisioner.getIfAvailable()).thenReturn(provisioner);
        org.mockito.Mockito.doThrow(new IllegalStateException("identity provider is down"))
                .when(provisioner).provisionAdmin(any(), any());

        // An organisation nobody can log into is worse than none: it looks
        // finished. Letting the exception out rolls the transaction back.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tenancyService.createTenant(
                        new CreateTenantCommand("Acme Corp", "SILO", "eu-west-1", "boss@acme.vn")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createTenantWithoutAnAdminEmailCreatesNobody() {
        authenticateAsSuperAdmin();
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        tenancyService.createTenant(new CreateTenantCommand("Acme Corp", "SILO", "eu-west-1", null));

        verify(adminProvisioner, org.mockito.Mockito.never()).getIfAvailable();
    }

    @Test
    void createTenantPersistsTenantInProvisioningStatus() {
        // Arrange
        authenticateAsSuperAdmin();
        CreateTenantCommand command = new CreateTenantCommand("Acme Corp", "SILO", "eu-west-1", null);
        // save() returns the entity it was given
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        TenantView view = tenancyService.createTenant(command);

        // Assert: repository.save was invoked with a correctly built Tenant
        verify(tenantRepository).save(tenantCaptor.capture());
        Tenant persisted = tenantCaptor.getValue();
        assertThat(persisted.getName()).isEqualTo("Acme Corp");
        assertThat(persisted.getTier()).isEqualTo(TenantTier.SILO);
        assertThat(persisted.getDataRegion()).isEqualTo("eu-west-1");
        assertThat(persisted.getStatus()).isEqualTo(TenantStatus.PROVISIONING);
        // The business tenantId mirrors the generated id
        assertThat(persisted.getTenantId()).isEqualTo(persisted.getId());

        // Assert: returned view reflects the persisted tenant
        assertThat(view.id()).isEqualTo(persisted.getId());
        assertThat(view.name()).isEqualTo("Acme Corp");
        assertThat(view.tier()).isEqualTo("SILO");
        assertThat(view.dataRegion()).isEqualTo("eu-west-1");
        assertThat(view.status()).isEqualTo("PROVISIONING");
    }

    @Test
    void createTenantAcceptsALowercaseTierFromTheWireContract() {
        // Arrange
        authenticateAsSuperAdmin();
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        TenantView view = tenancyService.createTenant(
                new CreateTenantCommand("Acme Corp", "pool", "eu-west-1", null));

        // Assert
        assertThat(view.tier()).isEqualTo("POOL");
    }

    @Test
    void createTenantWhenTierOutsideTheEnumIsRejectedAsBadRequestAndNothingIsWritten() {
        // Arrange: "basic" is not a tier — this used to surface as a 500.
        authenticateAsSuperAdmin();
        CreateTenantCommand command = new CreateTenantCommand("Acme Corp", "basic", "eu-west-1", null);

        // Act + Assert
        assertThatThrownBy(() -> tenancyService.createTenant(command))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("tier")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void createTenantWhenNameMissingIsRejectedAsBadRequestAndNothingIsWritten() {
        // Arrange
        authenticateAsSuperAdmin();
        CreateTenantCommand command = new CreateTenantCommand("  ", "POOL", "eu-west-1", null);

        // Act + Assert
        assertThatThrownBy(() -> tenancyService.createTenant(command))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("name");
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void getFeatureFlagsMapsAllFlagsOfTenantToViews() {
        // Arrange
        UUID tenantId = TENANT_ID;
        FeatureFlag aiFlag = new FeatureFlag(UUID.randomUUID(), tenantId, "ai.assistant", true);
        FeatureFlag smsFlag = new FeatureFlag(UUID.randomUUID(), tenantId, "sms.campaign", false);
        when(featureFlagRepository.findByTenantId(tenantId)).thenReturn(List.of(aiFlag, smsFlag));

        // Act
        List<FeatureFlagView> flags = tenancyService.getFeatureFlags(tenantId);

        // Assert
        assertThat(flags).containsExactly(
                new FeatureFlagView("ai.assistant", true),
                new FeatureFlagView("sms.campaign", false));
    }

    @Test
    void isEnabledWhenFlagPresentAndEnabledReturnsTrue() {
        // Arrange
        UUID tenantId = TENANT_ID;
        FeatureFlag flag = new FeatureFlag(UUID.randomUUID(), tenantId, "ai.assistant", true);
        when(featureFlagRepository.findByTenantIdAndKey(tenantId, "ai.assistant"))
                .thenReturn(Optional.of(flag));

        // Act + Assert
        assertThat(tenancyService.isEnabled(tenantId, "ai.assistant")).isTrue();
    }

    @Test
    void isEnabledWhenFlagPresentButDisabledReturnsFalse() {
        // Arrange
        UUID tenantId = TENANT_ID;
        FeatureFlag flag = new FeatureFlag(UUID.randomUUID(), tenantId, "sms.campaign", false);
        when(featureFlagRepository.findByTenantIdAndKey(tenantId, "sms.campaign"))
                .thenReturn(Optional.of(flag));

        // Act + Assert
        assertThat(tenancyService.isEnabled(tenantId, "sms.campaign")).isFalse();
    }

    @Test
    void isEnabledWhenFlagMissingReturnsFalse() {
        // Arrange
        UUID tenantId = TENANT_ID;
        when(featureFlagRepository.findByTenantIdAndKey(tenantId, "unknown"))
                .thenReturn(Optional.empty());

        // Act + Assert
        assertThat(tenancyService.isEnabled(tenantId, "unknown")).isFalse();
    }

    @Test
    void getThresholdsWhenNoneCreatesAndReturnsDefaults() {
        // Arrange
        when(businessThresholdsRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());
        when(businessThresholdsRepository.save(any(BusinessThresholds.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        BusinessThresholdsView view = tenancyService.getThresholds(TENANT_ID);

        // Assert: sensible defaults are persisted
        assertThat(view.riskAlertScore()).isEqualTo(60);
        assertThat(view.passScorePct()).isEqualTo(70);
        assertThat(view.minCampaignsPerQuarter()).isEqualTo(2);
        verify(businessThresholdsRepository).save(any(BusinessThresholds.class));
    }

    @Test
    void updateThresholdsOverridesProvidedFieldsAndClamps() {
        // Arrange: existing row, patch only two fields (one out of range)
        BusinessThresholds existing = new BusinessThresholds(UUID.randomUUID(), TENANT_ID, 60, 70, 2);
        when(businessThresholdsRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(existing));
        when(businessThresholdsRepository.save(any(BusinessThresholds.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act: risk 150 → clamped to 100; pass null → unchanged; campaigns 4
        BusinessThresholdsView view = tenancyService.updateThresholds(
                TENANT_ID, new BusinessThresholdsView(150, null, 4));

        // Assert
        assertThat(view.riskAlertScore()).isEqualTo(100);
        assertThat(view.passScorePct()).isEqualTo(70);
        assertThat(view.minCampaignsPerQuarter()).isEqualTo(4);
    }
}
