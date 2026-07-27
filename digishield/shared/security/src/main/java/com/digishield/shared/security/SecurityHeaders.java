package com.digishield.shared.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * Response headers applied to every chain, so the two profiles cannot drift.
 *
 * <p>The weekly ZAP baseline reported both of these missing. Spring Security
 * sets a useful default set — {@code X-Content-Type-Options},
 * {@code X-Frame-Options}, {@code Cache-Control: no-store} — but neither a
 * Content-Security-Policy nor a Permissions-Policy, because neither has a
 * default that is right for every application.
 *
 * <p>The policy here is tight because this application is a JSON API plus one
 * HTML page: the landing page shown after a simulated phishing link is clicked.
 * That page runs no scripts, so {@code default-src 'none'} costs nothing and
 * removes script execution entirely. It does style every element with inline
 * {@code style=} attributes, so {@code style-src} has to allow those or the page
 * arrives unformatted — a deliberate, narrow exception rather than a blanket
 * {@code unsafe-inline} across all directives.
 */
public final class SecurityHeaders {

    /**
     * No scripts, no plugins, no external loads; inline styles only, for the
     * one page that needs them.
     */
    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; style-src 'unsafe-inline'; img-src 'self' data:; "
                    + "base-uri 'none'; form-action 'none'";

    /** Nothing here needs a camera, a location or a payment handler. */
    private static final String PERMISSIONS_POLICY =
            "accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), "
                    + "microphone=(), payment=(), usb=()";

    private SecurityHeaders() {
    }

    /**
     * Applies the shared headers.
     *
     * @param http           chain being built
     * @param frameAncestors value for the CSP {@code frame-ancestors} directive
     *                       — {@code 'none'} in production; the dev chain has to
     *                       allow {@code 'self'} so the H2 console, which renders
     *                       in a frameset, still works
     */
    public static void apply(HttpSecurity http, String frameAncestors) throws Exception {
        http.headers(headers -> headers
                .contentSecurityPolicy(csp ->
                        csp.policyDirectives(CONTENT_SECURITY_POLICY
                                + "; frame-ancestors " + frameAncestors))
                .permissionsPolicyHeader(policy -> policy.policy(PERMISSIONS_POLICY))
                .referrerPolicy(referrer -> referrer.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                .ReferrerPolicy.NO_REFERRER)));
    }

    /** Production default: never framed by anyone. */
    public static void apply(HttpSecurity http) throws Exception {
        apply(http, "'none'");
    }
}
