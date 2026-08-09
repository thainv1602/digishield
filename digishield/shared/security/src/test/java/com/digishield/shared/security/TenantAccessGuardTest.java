package com.digishield.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.digishield.shared.tenantcontext.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TenantAccessGuard} — the {@code @tenantGuard.isSelf(#tenantId)}
 * check behind the hybrid tenancy authorization.
 */
class TenantAccessGuardTest {

    private final TenantAccessGuard guard = new TenantAccessGuard();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void isSelfTrueWhenTenantMatchesCallerContext() {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant.toString());
        assertThat(guard.isSelf(tenant)).isTrue();
    }

    @Test
    void isSelfFalseWhenTenantIsADifferentTenant() {
        TenantContext.set(UUID.randomUUID().toString());
        assertThat(guard.isSelf(UUID.randomUUID())).isFalse();
    }

    @Test
    void isSelfFalseWhenNoTenantContext() {
        assertThat(guard.isSelf(UUID.randomUUID())).isFalse();
    }

    @Test
    void isSelfFalseWhenTenantIdIsNull() {
        TenantContext.set(UUID.randomUUID().toString());
        assertThat(guard.isSelf(null)).isFalse();
    }
}
