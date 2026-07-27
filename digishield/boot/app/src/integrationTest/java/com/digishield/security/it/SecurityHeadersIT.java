package com.digishield.security.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Proves the security headers reach a real response, through the real chain.
 *
 * <p>The weekly ZAP baseline reported Content-Security-Policy and
 * Permissions-Policy missing. Configuring them compiles whether or not they are
 * ever written, and the scan that would notice runs once a week against a stack
 * this suite does not build — so without this, the next evidence either way
 * would have been a Monday.
 *
 * <p>The whole application is started rather than a web slice, and the chain is
 * the production {@code SecurityConfig} rather than the permissive dev one the
 * scan happens to exercise. A slice would have needed its own security wiring,
 * which is the part being tested; proving headers on scaffolding built for the
 * test proves nothing about what ships.
 *
 * <p>No issuer is configured here, so this exercises the locked-down branch of
 * {@code SecurityConfig} and the request comes back 403 with no body. That is
 * the point being made: the headers are written on every response whichever
 * branch runs. Rendering the landing page needs a reachable endpoint, so the
 * policy's stylesheet hash is checked in {@code SimTrackingPageCspIT} instead —
 * against the dev chain, which is also the one the weekly scan exercises.
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
                "management.health.redis.enabled=false"
        })
@AutoConfigureMockMvc
class SecurityHeadersIT {

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        /** Production chain is a JWT resource server; this request presents no token. */
        @org.springframework.context.annotation.Bean
        public org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder() {
            return org.mockito.Mockito.mock(org.springframework.security.oauth2.jwt.JwtDecoder.class);
        }
    }

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

    @Test
    void everyResponseCarriesAContentSecurityPolicy() throws Exception {
        mockMvc.perform(get("/api/v1/sim/track/{token}", UUID.randomUUID()))
                // Scripts are blocked outright; this page runs none.
                .andExpect(header().string("Content-Security-Policy",
                        Matchers.containsString("default-src 'none'")))
                // The page names its own stylesheet by hash, so nothing here
                // has to allow inline styles.
                .andExpect(header().string("Content-Security-Policy",
                        Matchers.containsString("style-src 'sha256-")))
                .andExpect(header().string("Content-Security-Policy",
                        Matchers.not(Matchers.containsString("unsafe-inline"))))
                .andExpect(header().string("Content-Security-Policy",
                        Matchers.containsString("frame-ancestors 'none'")));
    }

    @Test
    void everyResponseCarriesAPermissionsPolicy() throws Exception {
        mockMvc.perform(get("/api/v1/sim/track/{token}", UUID.randomUUID()))
                .andExpect(header().string("Permissions-Policy",
                        Matchers.containsString("camera=()")));
    }
}
