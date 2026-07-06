package com.digishield.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

/**
 * Role hierarchy for {@code @PreAuthorize} checks, so a higher role automatically
 * satisfies a lower one and endpoints only need to name the <em>minimum</em> role.
 * <ul>
 *   <li>{@code SUPER_ADMIN} ⊃ {@code ORG_ADMIN} ⊃ ({@code MANAGER}, {@code ANALYST},
 *       {@code CONTENT_EDITOR}) ⊃ {@code LEARNER}.</li>
 * </ul>
 *
 * <p>Active only for the non-{@code dev} profiles — method security itself lives on
 * the {@code @Profile("!dev")} {@code SecurityConfig}, so {@code @PreAuthorize} is
 * inert in the permissive {@code dev}/prod-like profile and only enforces in prod.
 * Kept in its own config so it doesn't collide with the resource-server wiring.
 * Spring Security auto-detects this {@link RoleHierarchy} bean for method security.
 */
@Configuration
@Profile("!dev")
public class MethodSecurityConfig {

    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_SUPER_ADMIN > ROLE_ORG_ADMIN
                ROLE_ORG_ADMIN > ROLE_MANAGER
                ROLE_ORG_ADMIN > ROLE_ANALYST
                ROLE_ORG_ADMIN > ROLE_CONTENT_EDITOR
                ROLE_MANAGER > ROLE_LEARNER
                ROLE_ANALYST > ROLE_LEARNER
                ROLE_CONTENT_EDITOR > ROLE_LEARNER
                """);
    }
}
