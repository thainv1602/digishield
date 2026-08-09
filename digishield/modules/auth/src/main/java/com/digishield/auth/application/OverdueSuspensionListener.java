package com.digishield.auth.application;

import com.digishield.auth.api.AuthService;
import com.digishield.auth.api.UserView;
import com.digishield.auth.domain.Role;
import com.digishield.contracts.events.EnrollmentDueEvent;
import com.digishield.shared.tenantcontext.TenantContext;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Locks out a learner whose mandatory training has passed its deadline.
 *
 * <p>Off unless {@code digishield.auth.suspend-overdue-learners.enabled} is set.
 * Turning it on takes real people's access away on a timer, which is a decision
 * for a deployment rather than a default.
 *
 * <p>The suspension has no self-service exit. A disabled account cannot sign in
 * at all, including into the training it was suspended over, so an administrator
 * has to restore the account before the person can clear the block that caused
 * it. That is a deliberate trade, not an oversight: anything else means leaving
 * a door open for exactly the accounts being locked.
 */
@Component
public class OverdueSuspensionListener {

    private static final Logger LOG = LoggerFactory.getLogger(OverdueSuspensionListener.class);

    private final AuthService authService;
    private final boolean enabled;

    public OverdueSuspensionListener(
            AuthService authService,
            @Value("${digishield.auth.suspend-overdue-learners.enabled:false}") boolean enabled) {
        this.authService = authService;
        this.enabled = enabled;
    }

    @ApplicationModuleListener
    public void on(EnrollmentDueEvent event) {
        if (!enabled || !event.overdue()) {
            return;
        }
        // The sweep runs without a caller, so the rank guards inside setSuspended
        // see no authenticated role and stand aside. The tenant still has to be
        // set: everything below reads it to scope the lookup.
        TenantContext.set(event.tenantId().toString());
        try {
            UserView user = authService.getUser(event.userId());
            if (!isPlainLearner(user)) {
                // Anyone who can administer users is left alone. The sweep runs
                // with no caller, so the guard that stops an admin suspending
                // themselves through the API does not apply here -- and a tenant
                // whose only admin is locked out has nobody left who can undo it.
                LOG.info("[auth] Not suspending {} for overdue training: role {} is not a learner",
                        event.userId(), user.role());
                return;
            }
            authService.setSuspended(event.userId(), true, "overdue training");
            LOG.info("[auth] Suspended user {} for training overdue since {}",
                    event.userId(), event.dueAt());
        } catch (NoSuchElementException e) {
            // The learner has been deleted since the sweep read the enrollment.
            LOG.info("[auth] No user {} to suspend for overdue training", event.userId());
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * True only for a role that is exactly {@code learner}.
     *
     * <p>Deliberately an exact match rather than {@code Role.fromWireName}, which
     * answers LEARNER for anything it does not recognise. Read that way, a null
     * or misspelt role would be locked out, and the bias here has to run the
     * other way: when it is unclear who someone is, leave their access alone.
     */
    private static boolean isPlainLearner(UserView user) {
        return user != null && Role.LEARNER.wireName().equals(user.role());
    }
}
