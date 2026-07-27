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
 * <p>The policy here is tight because this application serves JSON and nothing
 * else: no scripts, no styles, no external loads. The single HTML page it does
 * produce sets its own policy, naming the hash of its one stylesheet, so this
 * one does not have to be loosened on its behalf.
 */
public final class SecurityHeaders {

    /**
     * No scripts, no plugins, no external loads, and no styles either: every
     * response this covers is JSON.
     * <p>
     * There is one HTML page in the application — the landing page shown after
     * a simulated phishing link is clicked — and it sets its own policy naming
     * the SHA-256 of its stylesheet. Granting {@code unsafe-inline} here for its
     * sake would have weakened the policy on every JSON response to accommodate
     * a single page, which is what the ZAP baseline objected to.
     */
    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; img-src 'self' data:; "
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
    public static void apply(HttpSecurity http, String frameAncestors,
                             java.util.Collection<CspStyleSource> styleSources) throws Exception {
        String contentPolicy = CONTENT_SECURITY_POLICY + styleSrc(styleSources)
                + "; frame-ancestors " + frameAncestors;
        http.headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(contentPolicy))
                .permissionsPolicyHeader(policy -> policy.policy(PERMISSIONS_POLICY))
                .referrerPolicy(referrer -> referrer.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                .ReferrerPolicy.NO_REFERRER)));
    }

    /** Production default: never framed by anyone. */
    public static void apply(HttpSecurity http, java.util.Collection<CspStyleSource> styleSources)
            throws Exception {
        apply(http, "'none'", styleSources);
    }

    /**
     * Names each contributed stylesheet by hash, so exactly those load and
     * nothing else. Omitted entirely when nothing contributes one, leaving
     * style-src to inherit {@code default-src 'none'}.
     */
    private static String styleSrc(java.util.Collection<CspStyleSource> styleSources) {
        if (styleSources == null || styleSources.isEmpty()) {
            return "";
        }
        String hashes = styleSources.stream()
                .map(CspStyleSource::stylesheet)
                .filter(sheet -> sheet != null && !sheet.isBlank())
                .map(SecurityHeaders::sha256Base64)
                .map(hash -> "'sha256-" + hash + "'")
                .collect(java.util.stream.Collectors.joining(" "));
        return hashes.isEmpty() ? "" : "; style-src " + hashes;
    }

    private static String sha256Base64(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 is required of every JVM; this cannot happen.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
