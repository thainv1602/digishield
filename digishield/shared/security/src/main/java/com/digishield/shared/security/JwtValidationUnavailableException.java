package com.digishield.shared.security;

import org.springframework.security.oauth2.jwt.BadJwtException;

/**
 * Signals that the service cannot validate bearer tokens <em>at all</em> right
 * now — the decoder could not be built because the issuer is unreachable — as
 * opposed to the caller's token being wrong.
 *
 * <p>Extends {@link BadJwtException} deliberately. Spring Security's
 * {@code JwtAuthenticationProvider} converts a {@code BadJwtException} into an
 * {@code InvalidBearerTokenException} (the 401 path) and any other
 * {@code JwtException} into an {@code AuthenticationServiceException} (a 500).
 * Staying on the 401 path keeps an outage out of the 5xx bucket, and
 * {@link JwtUnavailableEntryPoint} then recognises this type in the cause chain
 * and answers {@code 503} instead of the misleading {@code 401}.
 */
public class JwtValidationUnavailableException extends BadJwtException {

    private static final long serialVersionUID = 1L;

    public JwtValidationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
