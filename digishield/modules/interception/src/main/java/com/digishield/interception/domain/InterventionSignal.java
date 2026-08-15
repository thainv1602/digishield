package com.digishield.interception.domain;

import java.util.Locale;

/**
 * Why an intervention fired.
 *
 * <p>These were bare string literals, produced in
 * {@code InterceptionServiceImpl.evaluate} and re-derived when reading
 * {@code intervention_event.signals} back. Nothing tied the two together, and
 * they drifted: {@code POST /interventions/evaluate} answered
 * {@code ["ON_CALL"]} while {@code GET /interventions} answered
 * {@code ["on_call"]} for the very same event. Anything counting signals across
 * both saw two populations instead of one.
 *
 * <p>Stored by {@link #name()} — upper case, like every other enum this
 * codebase persists — and put on the wire by {@link #wireName()}, which is what
 * the OpenAPI spec declares. Rows written before this enum existed already hold
 * the same upper-case spellings, so nothing needs rewriting.
 */
public enum InterventionSignal {

    /** The payer was on a phone call while paying — the classic coached-transfer tell. */
    ON_CALL,

    /** The destination account has never been paid by this user before. */
    NEW_PAYEE,

    /** The destination account is on the tenant's watchlist. */
    WATCHLIST_HIT;

    /**
     * The spelling used on the API, lower case — matching the
     * {@code InterventionDecision} and {@code InterventionEvent} schemas.
     */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
