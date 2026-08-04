package com.digishield;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import com.digishield.shared.security.JwtValidationUnavailableException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Production ({@code !dev}) WebSocket handshake interceptor.
 *
 * <p>Browsers cannot set an {@code Authorization} header on a WebSocket upgrade,
 * so the client passes its access token as the {@code access_token} query
 * parameter. This interceptor validates that token against the configured OpenID
 * issuer (the same {@code digishield.auth.jwt.issuer-uri} the REST resource
 * server uses) and resolves the tenant from the signed {@code tid} claim — the
 * WebSocket analogue of {@code TenantFilter}.
 *
 * <p><b>Fails closed.</b> If no issuer is configured (JWKS validation impossible)
 * or the token is missing/invalid/without a {@code tid} claim, the handshake is
 * rejected with {@code 401} and no session is established. Because the endpoint
 * is otherwise {@code permitAll} in the security chain, this interceptor is the
 * sole gate for the stream in production. When the issuer is configured but
 * unreachable the upgrade is refused with {@code 503} instead — mirroring the
 * REST chain, which must not report an outage as a rejected token.
 *
 * <p><b>Lazy decoder.</b> Building the decoder fetches the issuer's OIDC
 * discovery document over HTTP, so it must not happen in the constructor:
 * startup (including the Flyway migration job, which boots this context with
 * the {@code flyway} profile) would then depend on the IdP being reachable.
 * The decoder is built on the first handshake instead and memoized; while the
 * issuer is unreachable every upgrade is rejected and the next handshake
 * retries.
 */
@Component
@Profile("!dev")
public class JwtWsHandshakeInterceptor implements HandshakeInterceptor {

    /** JWT claim carrying the tenant id (matches {@code TenantFilter.TENANT_CLAIM}). */
    private static final String TENANT_CLAIM = "tid";

    private static final Logger LOG = LoggerFactory.getLogger(JwtWsHandshakeInterceptor.class);

    /** Null when no issuer is configured — the interceptor then denies every upgrade. */
    private final String issuerUri;

    /** Built lazily on first handshake; null until then (or while the issuer is unreachable). */
    private volatile JwtDecoder decoder;

    public JwtWsHandshakeInterceptor(@Value("${digishield.auth.jwt.issuer-uri:}") String issuerUri) {
        this.issuerUri = StringUtils.hasText(issuerUri) ? issuerUri.trim() : null;
        if (this.issuerUri == null) {
            LOG.warn("No JWT issuer configured (AUTH_JWT_ISSUER_URI unset) — WebSocket alert stream "
                    + "is disabled (every upgrade is rejected). Set the issuer to enable it.");
        }
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (issuerUri == null) {
            return deny(response, HttpStatus.UNAUTHORIZED);
        }
        JwtDecoder decoder;
        try {
            decoder = jwtDecoder();
        } catch (JwtValidationUnavailableException e) {
            // Same distinction the REST chain draws: the upgrade failed because
            // nothing can be validated right now, not because this token is bad.
            return deny(response, HttpStatus.SERVICE_UNAVAILABLE);
        }
        String token = tokenFrom(request);
        if (token == null) {
            return deny(response, HttpStatus.UNAUTHORIZED);
        }
        try {
            Jwt jwt = decoder.decode(token);
            String tenantId = jwt.getClaimAsString(TENANT_CLAIM);
            if (!StringUtils.hasText(tenantId)) {
                LOG.debug("WS handshake rejected: token has no '{}' claim", TENANT_CLAIM);
                return deny(response, HttpStatus.UNAUTHORIZED);
            }
            attributes.put(NotificationWebSocketHandler.ATTR_TENANT, tenantId);
            return true;
        } catch (JwtException e) {
            LOG.debug("WS handshake rejected: invalid token ({})", e.getMessage());
            return deny(response, HttpStatus.UNAUTHORIZED);
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    /**
     * Returns the memoized decoder, building it on first use.
     *
     * @throws JwtValidationUnavailableException when the issuer's discovery
     *     document cannot be fetched. Nothing is memoized on failure, so the next
     *     handshake retries; the caller answers 503 rather than 401 so the outage
     *     is not reported as a bad token. Only called with an issuer configured.
     */
    private JwtDecoder jwtDecoder() {
        JwtDecoder existing = decoder;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (decoder == null) {
                try {
                    decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
                } catch (RuntimeException e) {
                    LOG.warn("Could not initialise JWT decoder from issuer '{}' ({}); answering 503 "
                            + "for WebSocket upgrades until the issuer becomes reachable", issuerUri,
                            e.getMessage());
                    throw new JwtValidationUnavailableException(
                            "JWT validation unavailable: issuer unreachable", e);
                }
            }
            return decoder;
        }
    }

    private static String tokenFrom(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest http = servletRequest.getServletRequest();
            String token = http.getParameter("access_token");
            if (StringUtils.hasText(token)) {
                return token.trim();
            }
        }
        return null;
    }

    private static boolean deny(ServerHttpResponse response, HttpStatus status) {
        response.setStatusCode(status);
        return false;
    }
}
