package com.digishield.shared.tenantcontext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every listener and scheduled job overrides the tenant for the work it does and
 * puts back what it found. They used to clear instead, which is correct only on
 * a thread of their own: when {@code @Async} is inert the same code runs inline
 * on the caller's thread, and clearing there takes the caller's tenant with it.
 */
class TenantContextRestoreTest {

    private static final String CALLER = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER = "22222222-2222-2222-2222-222222222222";

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("an inline override gives the caller's tenant back")
    void restoresThePreviousTenant() {
        TenantContext.set(CALLER);

        String previous = TenantContext.get();
        TenantContext.set(OTHER);
        assertThat(TenantContext.get()).isEqualTo(OTHER);
        TenantContext.restore(previous);

        assertThat(TenantContext.get()).isEqualTo(CALLER);
    }

    @Test
    @DisplayName("on a thread that had no tenant, restoring clears it")
    void clearsWhenThereWasNothingToRestore() {
        // The async case: a listener on its own thread must leave nothing behind,
        // or the next task to borrow that thread inherits someone else's tenant.
        assertThat(TenantContext.get()).isNull();

        String previous = TenantContext.get();
        TenantContext.set(OTHER);
        TenantContext.restore(previous);

        assertThat(TenantContext.get()).isNull();
    }

    @Test
    @DisplayName("nested overrides unwind to the tenant each one found")
    void unwindsNestedOverrides() {
        TenantContext.set(CALLER);

        String outer = TenantContext.get();
        TenantContext.set(OTHER);
        String inner = TenantContext.get();
        TenantContext.set("33333333-3333-3333-3333-333333333333");
        TenantContext.restore(inner);
        assertThat(TenantContext.get()).isEqualTo(OTHER);
        TenantContext.restore(outer);

        assertThat(TenantContext.get()).isEqualTo(CALLER);
    }
}
