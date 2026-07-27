package com.digishield.shared.security;

import jakarta.servlet.DispatcherType;
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
    /** -1 when actuator shares the application port. */
    private final int managementPort;

    public SecurityConfig(
            @Value("${digishield.auth.jwt.issuer-uri:}") String issuerUri,
            @Value("${digishield.auth.jwt.audience:}") String audience,
            @Value("${digishield.auth.jwt.roles-claim:cognito:groups}") String rolesClaim,
            @Value("${management.server.port:}") String managementPort) {
        this.issuerUri = issuerUri;
        this.audience = audience;
        this.rolesClaim = rolesClaim;
        // Parsed rather than bound directly to an int. application.yml sets this
        // from ${MANAGEMENT_PORT:}, so with no env var the property is present
        // and *empty* — which is not the same as absent, so the :-1 default never
        // applied and Spring failed to convert "" to an int. That took down every
        // context without the variable, local runs included; the integration
        // tests caught it before it reached a cluster.
        this.managementPort = parsePort(managementPort);
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
                            // Servlet ERROR forwards are re-evaluated by the filter
                            // chain, so an anonymous request to a permitAll endpoint
                            // that fails validation (bad path variable, missing param)
                            // lands on /error and comes back 401 instead of the real
                            // 4xx. Permit the ERROR dispatch — not the /error path —
                            // so a direct GET /error still needs a token. Bodies carry
                            // no stack trace (server.error.include-stacktrace defaults
                            // to never).
                            .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                            // Only the health probes are anonymous — kubelet cannot
                            // present a token, and liveness/readiness expose nothing
                            // beyond up/down.
                            // Actuator on its own port, which the Ingress does not
                            // publish, is reachable only from inside the cluster —
                            // so an in-cluster scraper may read it without a token.
                            // Matching on the port the request arrived on, not the
                            // path, is what keeps the published port unaffected:
                            // /actuator/prometheus on 8080 is still admin-only.
                            //
                            // Separating the port alone does NOT do this. Spring
                            // applies this filter chain to the management context
                            // too, so 8081 answered 401 exactly like 8080 — which
                            // is how the metrics stack shipped broken in #142.
                            .requestMatchers(this::onManagementPort).permitAll()
                            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                            // The rest of actuator (metrics, prometheus, info) is
                            // operational telemetry: JVM internals, DB pool state,
                            // per-route request counts. Reachable from the public
                            // internet through the ingress, so it is admin-only
                            // rather than merely authenticated.
                            .requestMatchers("/actuator/**").hasRole(Roles.SUPER_ADMIN)
                            // QR image generator: encodes only the caller-supplied
                            // text into a picture; scanned by external devices, so
                            // it must be reachable without a bearer token.
                            .requestMatchers("/api/v1/qr").permitAll()
                            // Public simulation tracking links: an employee (often
                            // unauthenticated) follows the link from a simulated
                            // phishing email; the opaque token is the only secret.
                            .requestMatchers("/api/v1/sim/track/**").permitAll()
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
                    // Same ERROR-dispatch carve-out as the configured branch: a
                    // failing /actuator request would otherwise forward to /error,
                    // hit denyAll() and report an authorization failure instead of
                    // the real problem. Only the internal forward is permitted —
                    // a direct GET /error is still denied here.
                    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    // No issuer means no way to authenticate anyone, so only the
                    // health probes stay open — enough to keep the pod schedulable
                    // while the misconfiguration is visible. Telemetry stays shut.
                    .requestMatchers(this::onManagementPort).permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .anyRequest().denyAll());
        }
        SecurityHeaders.apply(http);
        return http.build();
    }

    /** Port number, or -1 when unset, empty or unparseable — meaning "shared port". */
    private static int parsePort(String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * True when the request arrived on the dedicated management port.
     *
     * <p>{@code -1} means actuator shares the application port, so this never
     * matches and the admin-only rules stand — a deployment that has not
     * separated the port keeps the stricter behaviour.
     */
    private boolean onManagementPort(jakarta.servlet.http.HttpServletRequest request) {
        return managementPort > 0 && request.getLocalPort() == managementPort;
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
