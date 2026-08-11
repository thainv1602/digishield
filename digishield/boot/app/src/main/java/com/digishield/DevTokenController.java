package com.digishield;

import com.digishield.auth.api.AuthProvider;
import com.digishield.auth.api.TokenPair;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hands out a signed token for local authorization testing.
 *
 * <p>{@code POST /api/v1/auth/login} is authenticated in production — the token
 * comes from the identity provider, not from this application — so there is no
 * anonymous way to obtain one. That is correct for production and useless
 * locally, where the whole point is to get a token for a chosen role and watch
 * the guards react.
 *
 * <p>Exists only under {@code dev-secure}, together with its own filter chain
 * permitting exactly this path. Production keeps a single chain in which
 * everything but the health probes is authenticated.
 */
@RestController
@Profile("dev-secure")
class DevTokenController {

    private final AuthProvider authProvider;

    DevTokenController(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    /**
     * Mints a token whose role comes from the email local part.
     *
     * @param email e.g. {@code analyst@dev.local}, {@code org_admin@dev.local}
     */
    @GetMapping("/dev/token")
    TokenPair token(@RequestParam("email") String email) {
        return authProvider.login(email, "");
    }

    /**
     * Ordered ahead of the application chain so {@code /dev/token} is reachable
     * without a token; every other path still falls through to the real chain.
     */
    @Bean
    @Order(0)
    SecurityFilterChain devTokenChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/dev/token")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
