package com.digishield;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Realtime fan-out, and the tenant boundary it has to hold.
 *
 * <p>This is one of the few places where a mistake leaks another organisation's
 * data without any query being wrong: the sockets are already open, and a
 * broadcast that picks the wrong bucket delivers straight to the browser.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationWebSocketHandlerTest {

    private static final String TENANT_A = UUID.randomUUID().toString();
    private static final String TENANT_B = UUID.randomUUID().toString();

    private final NotificationWebSocketHandler handler = new NotificationWebSocketHandler();

    private WebSocketSession sessionOf(String tenantId, boolean open) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        if (tenantId != null) {
            attributes.put(NotificationWebSocketHandler.ATTR_TENANT, tenantId);
        }
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(open);
        when(session.getId()).thenReturn(UUID.randomUUID().toString());
        return session;
    }

    @Test
    @DisplayName("a message reaches the connections of its own tenant and no others")
    void deliveryIsScopedToTheTenant() throws IOException {
        WebSocketSession a1 = sessionOf(TENANT_A, true);
        WebSocketSession a2 = sessionOf(TENANT_A, true);
        WebSocketSession b1 = sessionOf(TENANT_B, true);
        handler.afterConnectionEstablished(a1);
        handler.afterConnectionEstablished(a2);
        handler.afterConnectionEstablished(b1);

        int reached = handler.sendToTenant(TENANT_A, "{\"type\":\"alert\"}");

        assertThat(reached).isEqualTo(2);
        verify(a1).sendMessage(any(TextMessage.class));
        verify(a2).sendMessage(any(TextMessage.class));
        verify(b1, never()).sendMessage(any());
    }

    @Test
    @DisplayName("a tenant with nobody connected is not an error")
    void sendingToAnEmptyTenantReachesNobody() {
        assertThat(handler.sendToTenant(TENANT_A, "{}")).isZero();
    }

    @Test
    @DisplayName("a closed connection is dropped rather than counted as delivered")
    void closedSessionsAreNotCounted() throws IOException {
        WebSocketSession open = sessionOf(TENANT_A, true);
        WebSocketSession closed = sessionOf(TENANT_A, false);
        handler.afterConnectionEstablished(open);
        handler.afterConnectionEstablished(closed);

        assertThat(handler.sendToTenant(TENANT_A, "{}")).isEqualTo(1);
        verify(closed, never()).sendMessage(any());
    }

    @Test
    @DisplayName("one failing connection does not stop the rest of the tenant hearing it")
    void oneFailureDoesNotSilenceTheOthers() throws IOException {
        WebSocketSession broken = sessionOf(TENANT_A, true);
        WebSocketSession healthy = sessionOf(TENANT_A, true);
        doThrow(new IOException("pipe closed")).when(broken).sendMessage(any());
        handler.afterConnectionEstablished(broken);
        handler.afterConnectionEstablished(healthy);

        int reached = handler.sendToTenant(TENANT_A, "{}");

        assertThat(reached).isEqualTo(1);
        verify(healthy).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("a connection without a tenant is closed, not registered")
    void aTenantlessConnectionIsRefused() throws IOException {
        WebSocketSession anonymous = sessionOf(null, true);

        handler.afterConnectionEstablished(anonymous);

        verify(anonymous).close(any(CloseStatus.class));
        assertThat(handler.sendToTenant(TENANT_A, "{}")).isZero();
    }

    @Test
    @DisplayName("closing a connection removes it from the tenant's fan-out")
    void closingUnregisters() throws IOException {
        WebSocketSession session = sessionOf(TENANT_A, true);
        handler.afterConnectionEstablished(session);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertThat(handler.sendToTenant(TENANT_A, "{}")).isZero();
        verify(session, never()).sendMessage(any());
    }

    @Test
    void closingAConnectionThatWasNeverRegisteredIsHarmless() {
        handler.afterConnectionClosed(sessionOf(null, false), CloseStatus.NORMAL);

        assertThat(handler.sendToTenant(TENANT_A, "{}")).isZero();
    }
}
