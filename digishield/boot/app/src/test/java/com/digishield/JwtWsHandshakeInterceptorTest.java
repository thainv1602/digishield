package com.digishield;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The decoder must be built lazily: eager construction fetches the issuer's
 * OIDC discovery document over HTTP, which made every context boot (notably
 * the Flyway migration job) depend on the IdP being reachable and crashed the
 * ArgoCD PreSync hook when it was not.
 */
class JwtWsHandshakeInterceptorTest {

    /** Nothing listens here — any HTTP fetch fails fast with connection refused. */
    private static final String UNREACHABLE_ISSUER = "http://127.0.0.1:1/issuer";

    @Test
    void constructorDoesNotContactIssuer() {
        assertThatCode(() -> new JwtWsHandshakeInterceptor(UNREACHABLE_ISSUER))
                .doesNotThrowAnyException();
    }

    @Test
    void handshakeDeniedWhileIssuerUnreachable() {
        var interceptor = new JwtWsHandshakeInterceptor(UNREACHABLE_ISSUER);
        var response = new MockHttpServletResponse();

        boolean allowed = interceptor.beforeHandshake(
                requestWithToken(), new ServletServerHttpResponse(response), null, new HashMap<>());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void handshakeDeniedWhenNoIssuerConfigured() {
        var interceptor = new JwtWsHandshakeInterceptor("");
        var response = new MockHttpServletResponse();

        boolean allowed = interceptor.beforeHandshake(
                requestWithToken(), new ServletServerHttpResponse(response), null, new HashMap<>());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    private static ServletServerHttpRequest requestWithToken() {
        var servletRequest = new MockHttpServletRequest("GET", "/ws/notifications");
        servletRequest.setParameter("access_token", "some-token");
        return new ServletServerHttpRequest(servletRequest);
    }
}
