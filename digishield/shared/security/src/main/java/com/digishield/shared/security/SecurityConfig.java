package com.digishield.shared.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
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
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    /**
     * How long the last known good JWK set keeps validating tokens while the
     * identity provider is unreachable. Long enough to ride out a link outage,
     * short enough that a revoked signing key cannot be honoured indefinitely.
     */
    private static final Duration OUTAGE_TOLERANCE = Duration.ofHours(1);

    /** Bound on the one-off OpenID discovery call, so a hung provider cannot stall startup. */
    private static final Duration DISCOVERY_TIMEOUT = Duration.ofSeconds(10);

    private final String issuerUri;
    private final String audience;
    private final String rolesClaim;
    /** -1 when actuator shares the application port. */
    private final int managementPort;
    /**
     * A decoder supplied by the application, if any. Present in the
     * {@code dev-secure} profile, which mints and verifies its own tokens so
     * authorization can be exercised without an identity provider; absent in
     * production, where the issuer is discovered from {@code issuer-uri}.
     */
    private final ObjectProvider<JwtDecoder> suppliedDecoder;

    public SecurityConfig(
            @Value("${digishield.auth.jwt.issuer-uri:}") String issuerUri,
            @Value("${digishield.auth.jwt.audience:}") String audience,
            @Value("${digishield.auth.jwt.roles-claim:cognito:groups}") String rolesClaim,
            @Value("${management.server.port:}") String managementPort,
            ObjectProvider<JwtDecoder> suppliedDecoder) {
        this.suppliedDecoder = suppliedDecoder;
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
                                                   ObjectProvider<CorsConfigurationSource> corsSource,
                                                   ObjectProvider<CspStyleSource> styleSources)
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

        JwtDecoder supplied = suppliedDecoder.getIfAvailable();
        if (StringUtils.hasText(issuerUri) || supplied != null) {
            LOG.info("JWT resource server enabled — issuer={}, audience={}, rolesClaim={}",
                    supplied != null ? "(decoder bean)" : issuerUri,
                    StringUtils.hasText(audience) ? audience : "(any)", rolesClaim);
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
                    .oauth2ResourceServer(oauth2 -> oauth2
                            // Separates "we cannot validate tokens" (503) from
                            // "your token was rejected" (401). The SPA logs out
                            // on any 401, so answering 401 during an IdP outage
                            // turned every sign-in into an immediate bounce back
                            // to the login screen.
                            .authenticationEntryPoint(new JwtUnavailableEntryPoint())
                            .jwt(jwt -> jwt
                                    // Deferred: building the decoder fetches the issuer's
                                    // OpenID discovery document, and doing that here made
                                    // every context boot — including the Flyway migration
                                    // job's — depend on the IdP being reachable (crashed
                                    // the ArgoCD PreSync hook on the Jetson cluster).
                                    .decoder(supplied != null ? supplied : lazyJwtDecoder())
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
        SecurityHeaders.apply(http, styleSources.stream().toList());
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
     * Defers {@link #jwtDecoder()} to the first bearer token, so context startup
     * (notably the Flyway migration job's) never touches the network.
     *
     * <p>Fails closed while the issuer is unreachable: the build error is wrapped
     * in a {@link JwtValidationUnavailableException}, a {@link BadJwtException}
     * that keeps the resource server off the 500 path a raw
     * {@code JwtDecoderInitializationException} produces (Spring Security's own
     * {@code SupplierJwtDecoder} was observed doing exactly that), while
     * {@link JwtUnavailableEntryPoint} renders it as {@code 503} rather than a
     * {@code 401} that would blame the caller's token. Nothing is memoized on
     * failure, so the next request retries; the first successful build is reused
     * for the life of the context, and from then on outages are absorbed by the
     * JWK source's outage tolerance instead.
     */
    private JwtDecoder lazyJwtDecoder() {
        return new JwtDecoder() {
            private volatile JwtDecoder delegate;

            @Override
            public Jwt decode(String token) {
                JwtDecoder decoder = delegate;
                if (decoder == null) {
                    synchronized (this) {
                        if (delegate == null) {
                            try {
                                delegate = jwtDecoder();
                            } catch (RuntimeException e) {
                                LOG.warn("Could not initialise JWT decoder from issuer '{}' ({}); "
                                        + "answering 503 for bearer tokens until the issuer becomes reachable",
                                        issuerUri, e.getMessage());
                                throw new JwtValidationUnavailableException(
                                        "JWT validation unavailable: issuer unreachable", e);
                            }
                        }
                        decoder = delegate;
                    }
                }
                return decoder.decode(token);
            }
        };
    }

    /**
     * Decoder that resolves the issuer's JWKS and validates signature + issuer +
     * expiry, plus an optional audience/{@code client_id} check.
     *
     * <p>The JWK source is built explicitly rather than left to the Spring
     * defaults, which cache the key set for five minutes but disable both
     * refresh-ahead and outage tolerance. That combination puts a blocking call
     * to the identity provider on the request path every time the cache expires,
     * and turns any failure of that call into a 500 for a request whose token was
     * perfectly valid — observed on the Jetson cluster, whose link to Cognito is
     * slow enough to time out. Refresh-ahead moves the refetch onto a background
     * thread before expiry, and outage tolerance keeps serving the last known good
     * key set while the provider is unreachable. Unknown-{@code kid} tokens still
     * force an immediate refresh, so key rotation is unaffected.
     *
     * <p>Called lazily (via {@link #lazyJwtDecoder()}) on the first bearer token,
     * never during context startup: construction performs the OpenID discovery
     * calls, and startup must not depend on the IdP being reachable.
     */
    private NimbusJwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri)
                .jwtProcessorCustomizer(processor ->
                        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(
                                JWSAlgorithm.RS256, resilientJwkSource())))
                .build();
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(issuerUri));
        if (StringUtils.hasText(audience)) {
            validators.add(audienceValidator(audience));
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    /**
     * JWK source that refreshes ahead of expiry and survives a provider outage.
     *
     * @throws IllegalStateException when the issuer's JWKS location cannot be
     *     resolved. Surfaced by {@link #lazyJwtDecoder()} as a per-request 401
     *     and a warning in the logs, retried on the next request — so a
     *     misconfigured issuer is loud without an unreachable one taking the
     *     whole context down.
     */
    private JWKSource<SecurityContext> resilientJwkSource() {
        URL jwkSetUrl;
        try {
            jwkSetUrl = URI.create(jwkSetUri(issuerUri)).toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Issuer " + issuerUri + " advertises an unusable jwks_uri", e);
        }
        return JWKSourceBuilder.create(jwkSetUrl)
                .refreshAheadCache(true)
                .outageTolerant(OUTAGE_TOLERANCE.toMillis())
                .retrying(true)
                .build();
    }

    /**
     * Reads {@code jwks_uri} from the issuer's OpenID configuration.
     *
     * <p>Discovery runs when the decoder is first built — on the first bearer
     * token, not at startup. While the provider is unreachable every token is
     * rejected (401) and the next request retries; startup and the Flyway
     * migration job stay independent of the IdP.
     */
    private static String jwkSetUri(String issuerUri) {
        String metadataUri = issuerUri.replaceAll("/+$", "") + "/.well-known/openid-configuration";
        RestTemplate restTemplate = new RestTemplate();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(DISCOVERY_TIMEOUT);
        requestFactory.setReadTimeout(DISCOVERY_TIMEOUT);
        restTemplate.setRequestFactory(requestFactory);
        Map<?, ?> metadata = restTemplate.getForObject(metadataUri, Map.class);
        Object jwksUri = metadata == null ? null : metadata.get("jwks_uri");
        if (!(jwksUri instanceof String uri) || uri.isBlank()) {
            throw new IllegalStateException("No jwks_uri in the OpenID configuration at " + metadataUri);
        }
        return uri;
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
