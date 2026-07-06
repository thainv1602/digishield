package com.digishield;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Registers the real-time notification stream at {@code /ws/notifications}.
 *
 * <p>The upgrade is authenticated by the active profile's
 * {@link HandshakeInterceptor} — {@link DevWsHandshakeInterceptor} (tenant from a
 * query param / demo fallback) in {@code dev}, {@link JwtWsHandshakeInterceptor}
 * (validated access-token query param → {@code tid} claim) otherwise. Exactly one
 * interceptor bean is active per profile.
 *
 * <p>Allowed origins reuse {@code digishield.cors.allowed-origins} (the same list
 * the REST CORS config uses), so the Vite dev server / configured web origin can
 * open the socket while others are refused at handshake time.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler handler;
    private final HandshakeInterceptor handshakeInterceptor;
    private final List<String> allowedOrigins;

    public WebSocketConfig(NotificationWebSocketHandler handler,
                           HandshakeInterceptor handshakeInterceptor,
                           @Value("${digishield.cors.allowed-origins:http://localhost:5173}")
                           List<String> allowedOrigins) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/notifications")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }
}
