package com.digishield;

import com.digishield.shared.tenantcontext.DemoTenants;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Dev-profile WebSocket handshake interceptor.
 *
 * <p>There is no JWT in dev, so the tenant is taken from the {@code tenant} query
 * parameter (or the {@code X-Tenant-Id} header if present), falling back to the
 * fixed {@link DemoTenants#DEMO_TENANT_ID}. This mirrors {@code DevTenantFilter}
 * for the REST side and lets the frontend open the stream without authentication.
 *
 * <p>Only active in {@code dev}; the {@code !dev} profile uses
 * {@link JwtWsHandshakeInterceptor}, which validates a real token.
 */
@Component
@Profile("dev")
public class DevWsHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String tenantId = DemoTenants.DEMO_TENANT_ID.toString();
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest http = servletRequest.getServletRequest();
            String fromQuery = http.getParameter("tenant");
            String fromHeader = http.getHeader("X-Tenant-Id");
            if (fromQuery != null && !fromQuery.isBlank()) {
                tenantId = fromQuery.trim();
            } else if (fromHeader != null && !fromHeader.isBlank()) {
                tenantId = fromHeader.trim();
            }
        }
        attributes.put(NotificationWebSocketHandler.ATTR_TENANT, tenantId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
