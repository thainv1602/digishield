package com.digishield.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The entry point has one job: tell "we cannot validate tokens" (503) apart from
 * "your token was rejected" (401). Getting it wrong is not cosmetic — the SPA
 * signs the user out on any 401, so an outage reported as 401 bounces every
 * sign-in straight back to the login screen.
 */
class JwtUnavailableEntryPointTest {

    private final JwtUnavailableEntryPoint entryPoint = new JwtUnavailableEntryPoint();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    /**
     * The resource server never surfaces the decoder's exception directly — it
     * wraps it — so the entry point has to look down the cause chain.
     */
    @Test
    void answers503WhenValidationIsUnavailable() throws Exception {
        Exception decoderFailure = new JwtValidationUnavailableException(
                "JWT validation unavailable: issuer unreachable", new IllegalStateException("no route"));

        entryPoint.commence(request, response,
                new InvalidBearerTokenException(decoderFailure.getMessage(), decoderFailure));

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("30");
        // Nothing the caller could present would help, so this is no challenge.
        assertThat(response.getHeader("WWW-Authenticate")).isNull();
    }

    @Test
    void answers401WhenTheTokenItselfWasRejected() throws Exception {
        entryPoint.commence(request, response, new InvalidBearerTokenException("Malformed token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).contains("Bearer");
        assertThat(response.getHeader("Retry-After")).isNull();
    }

    @Test
    void answers401ForAnAnonymousRequest() throws Exception {
        entryPoint.commence(request, response, new BadCredentialsException("no token"));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    /** A cause chain that loops must not spin the request thread. */
    @Test
    void toleratesASelfReferencingCause() throws Exception {
        Exception looping = new IllegalStateException("boom") {
            private static final long serialVersionUID = 1L;

            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };

        entryPoint.commence(request, response, new InvalidBearerTokenException("wrapped", looping));

        assertThat(response.getStatus()).isEqualTo(401);
    }
}
