package com.digishield.shared.tenantcontext;

import java.util.UUID;

/**
 * Records that someone did something worth being able to answer for later.
 *
 * <p>The audit log lives in the tenancy module, but the actions worth auditing
 * happen all over — a role change in auth, a triage in reporting, a broadcast in
 * notification. Rather than have every module depend on tenancy, they depend on
 * this interface and the application shell supplies the implementation, the same
 * shape as the notification module's {@code RecipientResolver}.
 *
 * <p>Call sites state <em>what happened</em>; the implementation resolves who and
 * from where (authenticated principal, request IP, tenant context), so a caller
 * cannot get attribution wrong or forget it. Events that happen before there is
 * an authenticated principal — a failed login — use the explicit overload.
 *
 * <p>Recording must never break the action being recorded: implementations swallow
 * their own failures and log them instead.
 */
public interface AuditRecorder {

    /** Drives colour-coding on the audit screen and lets noisy events be filtered. */
    enum Severity {
        /** Routine, high-volume: a successful login. */
        STANDARD,
        /** Touches other people's data or security posture: triage, blacklist. */
        SENSITIVE,
        /** Changes who can do what, or platform state: role change, tenant suspend. */
        CRITICAL
    }

    /**
     * Records an action by the current caller, attributing it to the authenticated
     * principal and the current tenant.
     */
    void record(String action, String target, Severity severity);

    /**
     * Records an action with explicit attribution, for events with no authenticated
     * principal yet — a failed login knows only the email that was attempted, and
     * the tenant has to be looked up from it.
     *
     * @param tenantId tenant the entry belongs to; when {@code null} there is no
     *                 tenant to file it under (an attempt against an address that
     *                 belongs to nobody) and the implementation logs it instead
     */
    void record(UUID tenantId, String actor, String action, String target, Severity severity);
}
