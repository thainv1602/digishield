package com.digishield;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.context.annotation.Primary;

/**
 * CORS configuration for the DigiShield API.
 *
 * <p>Exposes a single {@link CorsConfigurationSource} bean that both security
 * filter chains (prod {@code SecurityConfig} and {@code DevSecurityConfig}) pick
 * up via {@code http.cors(...)}. This makes the React frontend
 * (Vite dev server at {@code http://localhost:5173}) able to call the API with
 * {@code Authorization} and {@code X-Tenant-Id} headers and credentials.
 *
 * <p>The allowed origins are configurable via {@code digishield.cors.allowed-origins}
 * (comma-separated). The {@code dev} profile sets it to the Vite dev server; in
 * prod it can be pointed at the real web origin(s) without code changes.
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(
            @Value("${digishield.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    @Primary
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Patterns, not plain origins: entries may use wildcards (e.g.
        // https://*.trycloudflare.com for the Jetson tunnel, whose subdomain
        // changes on every restart). Exact origins keep working unchanged, and
        // unlike a literal "*", patterns are allowed with credentials.
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Allow any request header, plus explicitly the headers the FE sends.
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "X-Tenant-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
