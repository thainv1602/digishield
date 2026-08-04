package com.digishield.security.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Proves the context boots with a JWT issuer configured but unreachable.
 *
 * <p>{@code SecurityConfig} used to build its decoder while creating the
 * security filter chain, which fetches the issuer's OpenID discovery document
 * over HTTP. With Cognito unreachable that failed the whole context — the
 * Flyway migration job died the same way, and its ArgoCD PreSync hook blocked
 * every rollout on the Jetson cluster ("Job has reached the specified backoff
 * limit"). The decoder is now built lazily on the first bearer token, so an
 * unreachable IdP must not prevent startup.
 *
 * <p>Unlike {@code SecurityHeadersIT} this configures an issuer (pointing at a
 * closed port, so any fetch fails fast) and supplies no mock decoder: the point
 * is that the real decoder path is deferred. While the issuer stays unreachable
 * the chain fails closed, and it says which failure it is: an anonymous request
 * is 401, while a bearer token gets 503 — the token was never judged, so calling
 * it unauthorized would be a lie. It matters because the SPA logs the user out on
 * any 401, which turned an IdP outage into "sign in, bounce straight back to the
 * login screen".
 *
 * <p>Requires Docker.
 */
@SpringBootTest(
        properties = {
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.flyway.enabled=true",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
                "spring.datasource.driver-class-name=org.postgresql.Driver",
                "spring.cache.type=none",
                "management.health.redis.enabled=false",
                // Nothing listens on port 1 — every discovery fetch fails fast.
                "digishield.auth.jwt.issuer-uri=http://127.0.0.1:1/issuer"
        })
@AutoConfigureMockMvc
class UnreachableIssuerStartupIT {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("digishield.rls.app-role", () -> "digishield_app");
    }

    /** Context startup itself is the regression under test; probes stay open. */
    @Test
    void healthProbeAnswersWithoutContactingTheIssuer() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousRequestIsRejectedNotErrored() throws Exception {
        mockMvc.perform(get("/api/v1/orgs"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 503, not 401: the decoder could not be built, so the token was never
     * judged. A 401 here would tell the client to discard a session that may be
     * perfectly valid.
     */
    @Test
    void bearerTokenGetsServiceUnavailableWhileIssuerIsDown() throws Exception {
        mockMvc.perform(get("/api/v1/orgs")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists("Retry-After"));
    }
}
