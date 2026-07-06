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
    void isSelf_true_whenTenantMatchesCallerContext() {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant.toString());
        assertThat(guard.isSelf(tenant)).isTrue();
    }

    @Test
    void isSelf_false_whenTenantIsADifferentTenant() {
        TenantContext.set(UUID.randomUUID().toString());
        assertThat(guard.isSelf(UUID.randomUUID())).isFalse();
    }

    @Test
    void isSelf_false_whenNoTenantContext() {
        assertThat(guard.isSelf(UUID.randomUUID())).isFalse();
    }

    @Test
    void isSelf_false_whenTenantIdIsNull() {
        TenantContext.set(UUID.randomUUID().toString());
        assertThat(guard.isSelf(null)).isFalse();
    }
}
