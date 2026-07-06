package com.digishield.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Production security configuration for the business modules:
 * <ul>
 *   <li>Stateless, using a JWT resource server validated against a configured
 *       OpenID issuer (e.g. an Amazon Cognito user pool).</li>
 *   <li>Allows access to {@code /actuator/**} without authentication (health/metrics).</li>
 *   <li>Enables method security ({@code @PreAuthorize}).</li>
 *   <li>Wires in the shared CORS configuration (if a {@link CorsConfigurationSource}
 *       bean is present) so the frontend can call the API in any profile.</li>
 * </ul>
 *
 * <p>The issuer is supplied by {@code digishield.auth.jwt.issuer-uri}
 * ({@code AUTH_JWT_ISSUER_URI}). When set, the resource server fetches the
 * issuer's JWKS and validates each token's signature, issuer and expiry; the
 * optional {@code audience} adds an audience/{@code client_id} check. Group
 * membership from the {@code roles-claim} (default {@code cognito:groups}) is
 * mapped to {@code ROLE_*} authorities so {@code @PreAuthorize("hasRole(...)")}
 * and the {@link Roles} constants work. The signed {@code tid} claim is consumed
 * separately by the tenant filter.
 *
 * <p>When no issuer is configured the chain <em>fails closed</em>: only
 * {@code /actuator/**} is reachable and every other request is denied, so a
 * non-{@code dev} deployment is never accidentally left wide open. The permissive
 * local experience lives in the {@code dev}-profile {@code DevSecurityConfig}.
 *
 * <p>Active for every profile <em>except</em> {@code dev}: the {@code dev} profile
 * supplies its own permissive {@code DevSecurityConfig} (in {@code boot:app}) and
 * having both chains active would create a bean conflict.
 */
@Configuration
@Profile("!dev")
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class);

    private final String issuerUri;
    private final String audience;
    private final String rolesClaim;

    public SecurityConfig(
            @Value("${digishield.auth.jwt.issuer-uri:}") String issuerUri,
            @Value("${digishield.auth.jwt.audience:}") String audience,
            @Value("${digishield.auth.jwt.roles-claim:cognito:groups}") String rolesClaim) {
        this.issuerUri = issuerUri;
        this.audience = audience;
        this.rolesClaim = rolesClaim;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ObjectProvider<CorsConfigurationSource> corsSource)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {
                    CorsConfigurationSource source = corsSource.getIfAvailable();
                    if (source != null) {
                        cors.configurationSource(source);
                    } else {
                        cors.disable();
                    }
                })
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (StringUtils.hasText(issuerUri)) {
            LOG.info("JWT resource server enabled — issuer={}, audience={}, rolesClaim={}",
                    issuerUri, StringUtils.hasText(audience) ? audience : "(any)", rolesClaim);
            http
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/actuator/**").permitAll()
                            // The WebSocket upgrade carries its token as a query param
                            // (browsers can't set Authorization on a WS handshake); the
                            // JwtWsHandshakeInterceptor validates it and fails closed.
                            .requestMatchers("/ws/**").permitAll()
                            .anyRequest().authenticated())
                    .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                            .decoder(jwtDecoder())
                            .jwtAuthenticationConverter(jwtAuthenticationConverter())));
        } else {
            LOG.warn("No JWT issuer configured (AUTH_JWT_ISSUER_URI unset) in a non-dev profile — "
                    + "API is locked down (actuator only). Set the issuer to enable authentication.");
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/**").permitAll()
                    .anyRequest().denyAll());
        }
        return http.build();
    }

    /**
     * Decoder that resolves the issuer's JWKS and validates signature + issuer +
     * expiry, plus an optional audience/{@code client_id} check.
     */
    private NimbusJwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(issuerUri));
        if (StringUtils.hasText(audience)) {
            validators.add(audienceValidator(audience));
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    /**
     * Accepts a token whose {@code aud} array contains, or whose {@code client_id}
     * equals, the expected audience — covering both Cognito ID tokens ({@code aud})
     * and access tokens ({@code client_id}).
     */
    private static OAuth2TokenValidator<Jwt> audienceValidator(String expected) {
        OAuth2Error error = new OAuth2Error("invalid_token",
                "Required audience '" + expected + "' is missing", null);
        return jwt -> {
            List<String> aud = jwt.getAudience();
            String clientId = jwt.getClaimAsString("client_id");
            if ((aud != null && aud.contains(expected)) || expected.equals(clientId)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(error);
        };
    }

    /**
     * Maps the identity provider's group claim (default {@code cognito:groups}) to
     * {@code ROLE_*} authorities so {@code hasRole(Roles.ORG_ADMIN)} matches a
     * Cognito group named {@code ORG_ADMIN}. Group names are upper-cased and
     * hyphens/spaces normalised to underscores.
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> groups = jwt.getClaimAsStringList(rolesClaim);
            List<GrantedAuthority> authorities = new ArrayList<>();
            if (groups != null) {
                for (String group : groups) {
                    if (StringUtils.hasText(group)) {
                        String role = group.trim().toUpperCase(Locale.ROOT)
                                .replace('-', '_').replace(' ', '_');
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                    }
                }
            }
            return authorities;
        });
        return converter;
    }
}
