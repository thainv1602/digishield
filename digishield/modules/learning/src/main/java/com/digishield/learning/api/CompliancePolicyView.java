package com.digishield.learning.api;

import java.util.UUID;

/**
 * Public view describing a compliance policy (Compliance screen).
 *
 * @param id            policy identifier
 * @param name          policy name
 * @param framework     mapped compliance framework
 * @param dueRule       human-readable due rule / deadline text
 * @param mandatory     whether the policy is mandatory
 * @param completionPct completion percentage (0..100)
 */
public record CompliancePolicyView(UUID id, String name, String framework, String dueRule,
                                   boolean mandatory, int completionPct) {
}
