package com.digishield;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket handler backing the real-time notification stream
 * ({@code /ws/notifications}).
 *
 * <p>Keeps a registry of open sessions keyed by tenant id so a broadcast alert
 * can be fanned out to exactly the clients of the originating tenant. The tenant
 * is resolved during the handshake (see the profile-specific
 * {@code HandshakeInterceptor}s) and stashed in the session attributes under
 * {@link #ATTR_TENANT}.
 *
 * <p>The stream is server→client only: inbound frames are ignored (the client
 * sends nothing but an occasional keep-alive). Each session is wrapped in a
 * {@link ConcurrentWebSocketSessionDecorator} so concurrent sends from the
 * fan-out are serialised safely.
 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    /** Session-attribute key holding the resolved tenant id (a UUID string). */
    public static final String ATTR_TENANT = "tenantId";

    private static final Logger LOG = LoggerFactory.getLogger(NotificationWebSocketHandler.class);
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024;

    /** tenantId -> live sessions. Concurrent set values so add/remove are lock-free. */
    private final Map<String, Set<WebSocketSession>> sessionsByTenant = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String tenantId = tenantOf(session);
        if (tenantId == null) {
            // Should not happen — the handshake interceptor rejects tenant-less upgrades.
            silentlyClose(session, CloseStatus.NOT_ACCEPTABLE.withReason("No tenant"));
            return;
        }
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
        sessionsByTenant.computeIfAbsent(tenantId, k -> ConcurrentHashMap.newKeySet()).add(safe);
        LOG.debug("WS connected: tenant={}, sessions={}", tenantId, sessionsByTenant.get(tenantId).size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String tenantId = tenantOf(session);
        if (tenantId == null) {
            return;
        }
        sessionsByTenant.computeIfPresent(tenantId, (k, sessions) -> {
            sessions.removeIf(s -> sameSession(s, session));
            return sessions.isEmpty() ? null : sessions;
        });
        LOG.debug("WS closed: tenant={}, status={}", tenantId, status);
    }

    /**
     * Sends a text frame to every open session of {@code tenantId}. Best-effort:
     * a failed send closes and drops that one session but never throws back to the
     * caller. Returns the number of clients the message reached.
     */
    public int sendToTenant(String tenantId, String payload) {
        Set<WebSocketSession> sessions = sessionsByTenant.get(tenantId);
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }
        TextMessage message = new TextMessage(payload);
        int reached = 0;
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                session.sendMessage(message);
                reached++;
            } catch (IOException | RuntimeException e) {
                LOG.debug("WS send to tenant {} failed; dropping session: {}", tenantId, e.getMessage());
                sessions.remove(session);
                silentlyClose(session, CloseStatus.SERVER_ERROR);
            }
        }
        return reached;
    }

    private static String tenantOf(WebSocketSession session) {
        Object tenant = session.getAttributes().get(ATTR_TENANT);
        return tenant != null ? tenant.toString() : null;
    }

    /**
     * The registered session and the one handed to {@code afterConnectionClosed}
     * may differ by decorator wrapping; compare by the underlying session id.
     */
    private static boolean sameSession(WebSocketSession a, WebSocketSession b) {
        return a.getId() != null && a.getId().equals(b.getId());
    }

    private static void silentlyClose(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException | RuntimeException ignored) {
            // best effort
        }
    }
}
