package com.digishield.shared.security;

/**
 * A stylesheet that must be allowed by the Content-Security-Policy.
 * <p>
 * Contributed by whichever module serves HTML — currently only the simulation
 * landing page — and bridged in the boot application, which is the one place
 * that may see both sides.
 * <p>
 * The stylesheet itself is handed over rather than a hash of it, so
 * {@link SecurityHeaders} does the hashing. A hash written down by hand drifts
 * the moment someone edits a colour, and the failure mode is a silently
 * unstyled page rather than an error.
 */
public interface CspStyleSource {

    /** Exact stylesheet text, byte for byte as the page emits it. */
    String stylesheet();
}
