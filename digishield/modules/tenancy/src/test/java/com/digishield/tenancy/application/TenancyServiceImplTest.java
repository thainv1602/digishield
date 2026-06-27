package com.digishield.tenancy.application;

import com.digishield.tenancy.api.CreateTenantCommand;
import com.digishield.tenancy.api.FeatureFlagView;
import com.digishield.tenancy.api.TenantView;
import com.digishield.tenancy.domain.FeatureFlag;
import com.digishield.tenancy.domain.Tenant;
import com.digishield.tenancy.domain.TenantStatus;
import com.digishield.tenancy.domain.TenantTier;
import com.digishield.tenancy.infrastructure.FeatureFlagRepository;
import com.digishield.tenancy.infrastructure.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private TenantRepository tenantRepository;

    @Mock
    private FeatureFlagRepository featureFlagRepository;

    @InjectMocks
    private TenancyServiceImpl tenancyService;

    @Captor
    private ArgumentCaptor<Tenant> tenantCaptor;

    @Test
    void createTenant_persistsTenantInProvisioningStatus() {
        // Arrange
        CreateTenantCommand command = new CreateTenantCommand("Acme Corp", "SILO", "eu-west-1");
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
    void getFeatureFlags_mapsAllFlagsOfTenantToViews() {
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
    void isEnabled_whenFlagPresentAndEnabled_returnsTrue() {
        // Arrange
        UUID tenantId = TENANT_ID;
        FeatureFlag flag = new FeatureFlag(UUID.randomUUID(), tenantId, "ai.assistant", true);
        when(featureFlagRepository.findByTenantIdAndKey(tenantId, "ai.assistant"))
                .thenReturn(Optional.of(flag));

        // Act + Assert
        assertThat(tenancyService.isEnabled(tenantId, "ai.assistant")).isTrue();
    }

    @Test
    void isEnabled_whenFlagPresentButDisabled_returnsFalse() {
        // Arrange
        UUID tenantId = TENANT_ID;
        FeatureFlag flag = new FeatureFlag(UUID.randomUUID(), tenantId, "sms.campaign", false);
        when(featureFlagRepository.findByTenantIdAndKey(tenantId, "sms.campaign"))
                .thenReturn(Optional.of(flag));

        // Act + Assert
        assertThat(tenancyService.isEnabled(tenantId, "sms.campaign")).isFalse();
    }

    @Test
    void isEnabled_whenFlagMissing_returnsFalse() {
        // Arrange
        UUID tenantId = TENANT_ID;
        when(featureFlagRepository.findByTenantIdAndKey(tenantId, "unknown"))
                .thenReturn(Optional.empty());

        // Act + Assert
        assertThat(tenancyService.isEnabled(tenantId, "unknown")).isFalse();
    }
}
