package com.digishield.auth.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.digishield.auth.api.AuthService;
import com.digishield.auth.api.UserView;
import com.digishield.contracts.events.EnrollmentDueEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link OverdueSuspensionListener}. */
class OverdueSuspensionListenerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private final AuthService authService = mock(AuthService.class);

    private OverdueSuspensionListener listener(boolean enabled) {
        return new OverdueSuspensionListener(authService, enabled);
    }

    private void userWithRole(String role) {
        when(authService.getUser(USER)).thenReturn(UserView.of(
                USER, TENANT, null, "u@x.com", "U", role, "active", null, "vi", null, 0));
    }

    private EnrollmentDueEvent overdue() {
        return new EnrollmentDueEvent(TENANT, USER, UUID.randomUUID(), Instant.now().minusSeconds(3600), true);
    }

    @Test
    void aLearnerPastTheirDeadlineIsSuspended() {
        userWithRole("learner");

        listener(true).on(overdue());

        verify(authService).setSuspended(eq(USER), eq(true), any());
    }

    @Test
    void anAdminIsNeverSuspendedAutomatically() {
        userWithRole("org_admin");

        listener(true).on(overdue());

        // The sweep has no caller, so the guard that stops an admin suspending
        // themselves through the API does not fire. A tenant whose only admin is
        // locked out has nobody left who can undo it.
        verify(authService, never()).setSuspended(any(), anyBoolean(), any());
    }

    @Test
    void anUnrecognisedRoleIsLeftAlone() {
        userWithRole(null);

        listener(true).on(overdue());

        // Role.fromWireName answers LEARNER for anything it does not know, which
        // would lock out a user whose role is missing. The bias runs the other way.
        verify(authService, never()).setSuspended(any(), anyBoolean(), any());
    }

    @Test
    void nothingHappensWhileTheFeatureIsOff() {
        listener(false).on(overdue());

        verify(authService, never()).getUser(any());
        verify(authService, never()).setSuspended(any(), anyBoolean(), any());
    }

    @Test
    void anAssignmentMerelyDueSoonIsNotSuspended() {
        listener(true).on(new EnrollmentDueEvent(
                TENANT, USER, UUID.randomUUID(), Instant.now().plusSeconds(3600), false));

        verify(authService, never()).setSuspended(any(), anyBoolean(), any());
    }
}
