package com.digishield.security.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the Super Tenant Console is reachable on the dev profile.
 *
 * <p>{@code permitAll()} lets a request through without authenticating it, so
 * the SecurityContext is empty unless the chain puts something there. Checks
 * that read authorities directly — rather than through {@code @PreAuthorize},
 * which is inert here because {@code @EnableMethodSecurity} is
 * {@code @Profile("!dev")} — then see no roles at all. {@code PlatformScope} is
 * one, and {@code GET /tenants} answered 500 with "A platform-scoped read
 * requires ROLE_SUPER_ADMIN" for every developer running the console locally.
 *
 * <p>No unit test could catch it: the fault was in which principal the dev
 * filter chain installs, so it only appears once the real chain runs. H2 rather
 * than a container, so this needs no Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DevPlatformScopeIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listingTenantsSucceedsWithoutASignedInSuperAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());
    }
}
