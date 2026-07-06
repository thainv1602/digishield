package com.digishield;

import com.digishield.notification.api.NotificationView;
import com.digishield.notification.api.RealtimeNotifier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * WebSocket-backed {@link RealtimeNotifier}. Serialises the alert to JSON and
 * fans it out to the tenant's connected clients via
 * {@link NotificationWebSocketHandler}.
 *
 * <p>Marked {@code @Primary} so it wins over the module's no-op default whenever
 * the boot application is on the classpath (always, in practice). Best-effort:
 * serialization/transport problems are logged, never rethrown.
 *
 * <p>Wire envelope: {@code {"kind":"alert","notification":<NotificationView>}}.
 */
@Component
@Primary
public class WebSocketRealtimeNotifier implements RealtimeNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketRealtimeNotifier.class);

    private final NotificationWebSocketHandler handler;

    /**
     * Dedicated wire-format mapper: JSR-310 enabled and dates as ISO-8601 strings,
     * matching what the REST layer emits (the frontend parses {@code scheduledAt}
     * with {@code new Date(iso)}). Kept separate from the shared {@code ObjectMapper}
     * bean (a bare mapper used for JSON columns) so this doesn't change its behaviour.
     */
    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    public WebSocketRealtimeNotifier(NotificationWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void publishAlert(UUID tenantId, NotificationView notification) {
        if (tenantId == null) {
            return;
        }
        String payload;
        try {
            payload = mapper.writeValueAsString(new AlertEnvelope("alert", notification));
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to serialise real-time alert for tenant {}: {}", tenantId, e.getMessage());
            return;
        }
        int reached = handler.sendToTenant(tenantId.toString(), payload);
        LOG.debug("Pushed alert to {} live client(s) in tenant {}", reached, tenantId);
    }

    /** Wire envelope for a pushed alert; {@code kind} lets the client branch on message type. */
    private record AlertEnvelope(String kind, NotificationView notification) {
    }
}
