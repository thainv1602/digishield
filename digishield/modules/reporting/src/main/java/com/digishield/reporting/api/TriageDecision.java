package com.digishield.reporting.api;

/**
 * What an analyst decided about a reported message.
 *
 * <p>This replaces the boolean the endpoint used to take. A boolean can only
 * express "threat or not", which forced quarantine — a threat the analyst is
 * not ready to confirm — to be recorded as a dismissal, losing the distinction
 * exactly where it matters most.
 */
public enum TriageDecision {
    /** A real threat: confirmed, rewarded, and fed into risk scoring. */
    CONFIRM_THREAT,
    /** A threat, but held for escalation: no reward, no risk-score movement. */
    QUARANTINE,
    /** Not a threat. */
    DISMISS
}
