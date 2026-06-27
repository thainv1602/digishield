package com.digishield.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of initializing MFA, matching the {@code /auth/mfa/setup} OpenAPI schema.
 *
 * <p>JSON shape: {@code { "secret": "...", "otpauth_url": "...", "qr_svg": "..." }}.
 * In the dev profile these are generated locally (a random base32 TOTP secret, a
 * sample {@code otpauth://} URL and an inline placeholder QR SVG); no MFA state is
 * actually persisted.
 *
 * @param secret     the base32-encoded TOTP shared secret
 * @param otpauthUrl the {@code otpauth://totp/...} provisioning URI
 * @param qrSvg      an inline SVG rendering of the QR code
 */
public record MfaSetupView(
        String secret,
        @JsonProperty("otpauth_url") String otpauthUrl,
        @JsonProperty("qr_svg") String qrSvg) {
}
