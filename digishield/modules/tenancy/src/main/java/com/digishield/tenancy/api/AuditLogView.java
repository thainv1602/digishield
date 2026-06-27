package com.digishield.tenancy.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Public view describing an audit-log entry (Audit Log screen).
 *
 * @param id       entry identifier
 * @param ts       timestamp
 * @param actor    actor (user email / id) who performed the action
 * @param action   action performed (e.g. broadcast_alert, tenant.suspend)
 * @param target   affected object (e.g. report:#4821)
 * @param ip       source IP address
 * @param severity color-coding severity: critical|sensitive|standard
 */
public record AuditLogView(UUID id, Instant ts, String actor, String action,
                           String target, String ip, String severity) {
}
