package com.digishield;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import com.digishield.shared.security.SecurityHeaders;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Permissive security filter chain for the {@code dev} profile.
 *
 * <p>Lets the React frontend exercise the whole API without a real JWT:
 * <ul>
 *   <li>{@code permitAll()} on every request (no authentication required).</li>
 *   <li>CSRF disabled (stateless JSON API + H2 console).</li>
 *   <li>CORS enabled using the shared {@link CorsConfigurationSource}
 *       (origin {@code http://localhost:5173}).</li>
 *   <li>Frame options disabled so the H2 web console renders.</li>
 * </ul>
 *
 * <p>Only active when {@code dev} is on; the production {@code SecurityConfig}
 * (JWT resource server) is {@code @Profile("!dev")}, so exactly one chain is
 * ever in the context.
 */
@Configuration
@Profile("dev")
public class DevSecurityConfig {

    @Bean
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http,
                                                      CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(frame -> frame.disable()))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        // Same headers as production so the two cannot drift, but framing is
        // allowed from the same origin: the H2 console this profile exists to
        // serve renders inside a frameset.
        SecurityHeaders.apply(http, "'self'");
        return http.build();
    }
}
