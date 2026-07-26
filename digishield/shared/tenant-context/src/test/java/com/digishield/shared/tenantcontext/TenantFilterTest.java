package com.digishield.shared.tenantcontext;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TenantFilter}: the tenant is taken from the JWT
 * {@code tid} claim, never from a client-supplied value — except for the
 * {@code X-Acting-Tenant} header, which is honoured only after the signed token
 * has been checked for {@code ROLE_SUPER_ADMIN}.
 *
 * <p>The security-critical assertions here are the negative ones. If the header
 * were ever honoured for an ordinary caller, any authenticated user could read
 * any tenant's data — the exact hole closed by removing the forgeable
 * {@code X-Tenant-Id} fallback.
 */
class TenantFilterTest {

    private static final String OWN_TENANT = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER_TENANT = "22222222-2222-2222-2222-222222222222";

    private final TenantFilter filter = new TenantFilter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** Request carrying no acting-tenant header. */
    private static HttpServletRequest plainRequest() {
        return requestWithActingTenant(null);
    }

    private static HttpServletRequest requestWithActingTenant(String acting) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(TenantFilter.ACTING_TENANT_HEADER)).thenReturn(acting);
        return request;
    }

    private static void authenticateWithTid(String tid, String... authorities) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none");
        if (tid != null) {
            builder.claim(TenantFilter.TENANT_CLAIM, tid);
        } else {
            builder.claim("sub", "user-1");
        }
        List<SimpleGrantedAuthority> granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        TestingAuthenticationToken auth =
                new TestingAuthenticationToken(builder.build(), null, List.copyOf(granted));
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ---- tid claim resolution (pre-existing behaviour) ----

    @Test
    void resolvesTenantFromJwtTidClaim() {
        authenticateWithTid(OWN_TENANT, "ROLE_ORG_ADMIN");

        assertThat(filter.resolveTenantId(plainRequest())).isEqualTo(OWN_TENANT);
    }

    @Test
    void returnsNullWhenNoAuthentication() {
        SecurityContextHolder.clearContext();

        assertThat(filter.resolveTenantId(plainRequest())).isNull();
    }

    @Test
    void returnsNullWhenPrincipalIsNotAJwt() {
        // A non-JWT principal (e.g. a forged username) must not yield a tenant.
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("someone", "creds"));

        assertThat(filter.resolveTenantId(plainRequest())).isNull();
    }

    @Test
    void returnsNullWhenJwtHasNoTidClaim() {
        authenticateWithTid(null, "ROLE_ORG_ADMIN");

        assertThat(filter.resolveTenantId(plainRequest())).isNull();
    }

    // ---- acting tenant (super admin only) ----

    @Test
    void ignoresTheActingTenantHeaderForANonSuperAdmin() {
        authenticateWithTid(OWN_TENANT, "ROLE_ORG_ADMIN");

        assertThat(filter.resolveTenantId(requestWithActingTenant(OTHER_TENANT)))
                .isEqualTo(OWN_TENANT);
    }

    @Test
    void ignoresTheActingTenantHeaderWhenUnauthenticated() {
        SecurityContextHolder.clearContext();

        assertThat(filter.resolveTenantId(requestWithActingTenant(OTHER_TENANT))).isNull();
    }

    @Test
    void honoursTheActingTenantHeaderForASuperAdmin() {
        authenticateWithTid(OWN_TENANT, "ROLE_SUPER_ADMIN");

        assertThat(filter.resolveTenantId(requestWithActingTenant(OTHER_TENANT)))
                .isEqualTo(OTHER_TENANT);
    }

    @Test
    void ignoresAMalformedActingTenantHeader() {
        authenticateWithTid(OWN_TENANT, "ROLE_SUPER_ADMIN");

        // Anything not a UUID would blow up the RLS GUC downstream.
        assertThat(filter.resolveTenantId(requestWithActingTenant("not-a-uuid; drop table")))
                .isEqualTo(OWN_TENANT);
    }

    @Test
    void ignoresABlankActingTenantHeader() {
        authenticateWithTid(OWN_TENANT, "ROLE_SUPER_ADMIN");

        assertThat(filter.resolveTenantId(requestWithActingTenant("   "))).isEqualTo(OWN_TENANT);
    }

    // ---- platform scope ----

    @Test
    void platformScopeRefusesToOpenForANonSuperAdmin() {
        authenticateWithTid(OWN_TENANT, "ROLE_ORG_ADMIN");

        assertThatThrownBy(() -> PlatformScope.call(() -> "should not run"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPER_ADMIN");
        assertThat(PlatformScope.isActive()).isFalse();
    }

    @Test
    void platformScopeRefusesWithoutAuthentication() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> PlatformScope.call(() -> "should not run"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void platformScopeOpensForASuperAdminAndClosesAfterwards() {
        authenticateWithTid(OWN_TENANT, "ROLE_SUPER_ADMIN");

        assertThat(PlatformScope.call(PlatformScope::isActive)).isTrue();
        // A leaked flag would silently disable RLS for later work on this thread.
        assertThat(PlatformScope.isActive()).isFalse();
    }

    @Test
    void platformScopeClosesEvenWhenTheQueryThrows() {
        authenticateWithTid(OWN_TENANT, "ROLE_SUPER_ADMIN");

        assertThatThrownBy(() -> PlatformScope.call(() -> {
            throw new IllegalArgumentException("query blew up");
        })).isInstanceOf(IllegalArgumentException.class);

        assertThat(PlatformScope.isActive()).isFalse();
    }
}
