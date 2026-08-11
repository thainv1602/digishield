package com.digishield.tenancy.application;

import com.digishield.shared.tenantcontext.PlatformScope;
import com.digishield.shared.tenantcontext.TenantContext;
import com.digishield.tenancy.api.ScimConfigView;
import com.digishield.tenancy.domain.ScimConfig;
import com.digishield.tenancy.infrastructure.ScimConfigRepository;
import com.digishield.tenancy.infrastructure.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Reading a tenant's SCIM / SSO settings.
 *
 * <p>Most tenants have never connected an identity provider, so "no row" is the
 * ordinary case rather than an error, and it has to answer with the truth the
 * screen can render: not connected, never synced. Returning nothing produced a
 * 200 with an empty body, and the console renders that as a blank card - no
 * status, no error, nothing to act on.
 */
@ExtendWith(MockitoExtension.class)
class ScimConfigLookupTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private ScimConfigRepository scimConfigRepository;

    @Mock
    private org.springframework.beans.factory.ObjectProvider<
            com.digishield.tenancy.api.TenantAdminProvisioner> adminProvisioner;

    @InjectMocks
    private TenancyServiceImpl tenancyService;

    @BeforeEach
    void setTenant() {
        TenantContext.set(TENANT.toString());
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAsSuperAdmin() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(
                "super", null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static ScimConfig connectedConfig(UUID tenantId) {
        return new ScimConfig(UUID.randomUUID(), tenantId, "Azure AD", true,
                "azure-tenant", "client-1", "https://scim.example.vn/v2",
                Instant.parse("2026-08-01T00:00:00Z"), 42, 0);
    }

    @Test
    @DisplayName("a tenant with no IdP connected still gets a config, not an empty body")
    void anUnconfiguredTenantIsNotConnectedRatherThanAbsent() {
        when(tenantRepository.existsById(TENANT)).thenReturn(true);
        when(scimConfigRepository.findByTenantId(TENANT)).thenReturn(Optional.empty());

        ScimConfigView view = tenancyService.getScimConfig(TENANT);

        assertThat(view).isNotNull();
        assertThat(view.tenantId()).isEqualTo(TENANT);
        assertThat(view.connected()).isFalse();
        // "never" is what the screen shows as the sync state; null would leave
        // the pill blank, which is the state this test exists to prevent.
        assertThat(view.syncStatus()).isEqualTo("never");
        assertThat(view.syncedUserCount()).isZero();
        assertThat(view.syncErrorCount()).isZero();
        assertThat(view.idpName()).isNull();
    }

    @Test
    @DisplayName("a tenant that does not exist is absent, so the controller can answer 404")
    void anUnknownTenantHasNoConfigAtAll() {
        when(tenantRepository.existsById(TENANT)).thenReturn(false);

        assertThat(tenancyService.getScimConfig(TENANT)).isNull();
    }

    @Test
    @DisplayName("a connected IdP is reported with its own values")
    void aConnectedIdpIsReturnedAsStored() {
        when(tenantRepository.existsById(TENANT)).thenReturn(true);
        when(scimConfigRepository.findByTenantId(TENANT))
                .thenReturn(Optional.of(connectedConfig(TENANT)));

        ScimConfigView view = tenancyService.getScimConfig(TENANT);

        assertThat(view.connected()).isTrue();
        assertThat(view.idpName()).isEqualTo("Azure AD");
        assertThat(view.syncedUserCount()).isEqualTo(42);
        assertThat(view.syncStatus()).isEqualTo("ok");
    }

    @Test
    @DisplayName("a super admin reads another tenant's config through the platform scope")
    void aSuperAdminReadsAcrossTenants() {
        // The console opens an organisation other than its own. Without the
        // platform scope RLS hides the row and a connected IdP reads as absent.
        // A mocked repository cannot show that, so what is asserted here is the
        // scope itself: the lookup has to happen inside it, or RLS still applies
        // against the real database.
        authenticateAsSuperAdmin();
        AtomicBoolean scopedWhenRead = new AtomicBoolean();
        when(tenantRepository.existsById(OTHER_TENANT)).thenReturn(true);
        when(scimConfigRepository.findByTenantId(OTHER_TENANT)).thenAnswer(invocation -> {
            scopedWhenRead.set(PlatformScope.isActive());
            return Optional.of(connectedConfig(OTHER_TENANT));
        });

        ScimConfigView view = tenancyService.getScimConfig(OTHER_TENANT);

        assertThat(scopedWhenRead).isTrue();
        assertThat(view.tenantId()).isEqualTo(OTHER_TENANT);
        assertThat(view.connected()).isTrue();
    }

    @Test
    @DisplayName("sync errors are reported as an error state, not as a healthy sync")
    void errorsAreVisibleInTheStatus() {
        when(tenantRepository.existsById(TENANT)).thenReturn(true);
        when(scimConfigRepository.findByTenantId(TENANT)).thenReturn(Optional.of(
                new ScimConfig(UUID.randomUUID(), TENANT, "Okta", true, "okta-1",
                        "client-2", "https://scim.example.vn/v2",
                        Instant.parse("2026-08-01T00:00:00Z"), 10, 3)));

        assertThat(tenancyService.getScimConfig(TENANT).syncStatus()).isEqualTo("error");
    }
}
