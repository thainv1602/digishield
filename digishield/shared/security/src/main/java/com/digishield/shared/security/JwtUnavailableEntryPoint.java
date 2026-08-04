package com.digishield.shared.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Answers {@code 503 Service Unavailable} when a request failed because the
 * service could not validate tokens at all, and delegates everything else to the
 * standard {@link BearerTokenAuthenticationEntryPoint} (401 plus the
 * {@code WWW-Authenticate} challenge).
 *
 * <p>Why the distinction earns its keep: the SPA signs the user out on <em>any</em>
 * 401. While the identity provider was unreachable every request came back 401 —
 * including requests carrying a perfectly good token — so signing in bounced
 * straight back to the login screen. A leftover DNS blackhole for Cognito did
 * exactly that to the Jetson cluster for four days. {@code 503} states the true
 * condition ("not your token; try again shortly"), which a client can retry with
 * the session intact.
 *
 * <p>{@code Retry-After} is advisory: the decoder is rebuilt on the very next
 * bearer token (nothing is memoized on failure), so recovery is immediate once
 * the issuer is reachable again.
 */
public class JwtUnavailableEntryPoint implements AuthenticationEntryPoint {

    /** Hint for clients; the server retries the decoder build on every request regardless. */
    private static final String RETRY_AFTER_SECONDS = "30";

    /** Bound on the cause walk, so a self-referencing cause cannot spin. */
    private static final int MAX_CAUSE_DEPTH = 10;

    private final AuthenticationEntryPoint delegate;

    public JwtUnavailableEntryPoint() {
        this(new BearerTokenAuthenticationEntryPoint());
    }

    JwtUnavailableEntryPoint(AuthenticationEntryPoint delegate) {
        this.delegate = delegate;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        if (!isValidationUnavailable(authException)) {
            delegate.commence(request, response, authException);
            return;
        }
        // No WWW-Authenticate challenge: nothing the caller can present would
        // help, so this is not an authentication challenge.
        response.setHeader(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
        response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Token validation is temporarily unavailable");
    }

    /**
     * True when {@link JwtValidationUnavailableException} appears anywhere in the
     * cause chain. It is never the top-level exception: the resource server wraps
     * whatever the decoder threw in an {@code InvalidBearerTokenException}.
     */
    private static boolean isValidationUnavailable(Throwable throwable) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof JwtValidationUnavailableException) {
                return true;
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
        return false;
    }
}
