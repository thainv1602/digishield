package com.digishield;

import com.digishield.auth.api.AuthProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * Prints a ready-to-use token per role when {@code dev-secure} starts.
 *
 * <p>The obvious alternative — an endpoint that hands tokens out — needs a
 * filter chain with CSRF disabled and {@code permitAll}, which is real attack
 * surface bought for a little convenience, and CodeQL was right to flag it. The
 * console is already trusted by whoever started the process, so the token goes
 * there instead and nothing new is exposed over HTTP.
 */
@Configuration
@Profile("dev-secure")
class DevSecureTokenPrinter {

    private static final Logger LOG = LoggerFactory.getLogger(DevSecureTokenPrinter.class);

    private static final List<String> ROLES =
            List.of("super_admin", "org_admin", "manager", "content_editor", "analyst", "learner");

    @Bean
    ApplicationRunner printDevTokens(AuthProvider authProvider) {
        return args -> {
            LOG.info("[dev-secure] Authorization is ENFORCED. Bearer tokens for local use:");
            for (String role : ROLES) {
                LOG.info("[dev-secure]   {} {}", String.format("%-14s", role),
                        authProvider.login(role + "@dev.local", "").accessToken());
            }
            LOG.info("[dev-secure] e.g. curl -H \"Authorization: Bearer <token>\" "
                    + "http://localhost:8080/api/v1/reports/phishing");
        };
    }
}
