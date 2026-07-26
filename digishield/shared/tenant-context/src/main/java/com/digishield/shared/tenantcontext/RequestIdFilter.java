package com.digishield.shared.tenantcontext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts a request id on every log line so the lines belonging to one request can
 * be pulled out of the pile.
 *
 * <p>Reuses an inbound {@code X-Request-Id} when the caller supplied one — a
 * proxy, or another service passing its own id along — otherwise mints one. The
 * id is echoed back on the response so a client (or an operator reading a
 * browser's network tab) can quote it when reporting a problem.
 *
 * <p>Runs first so the id is present for anything later in the chain, including
 * {@link TenantFilter} and any authentication failure. The MDC is cleared in a
 * {@code finally} block: servlet containers pool threads, and a leaked id would
 * mislabel the next, unrelated request.
 *
 * <p>Deliberately not Micrometer Tracing: Spring Boot 4.1 ships no Brave
 * auto-configuration (the tracing module carries {@code NoopTracerAutoConfiguration}
 * and no Brave classes), so the framework's correlation field stays empty. This
 * covers a request within one process; propagating an id across the RabbitMQ hop
 * to the worker is a separate piece of work.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    /** Header carrying a caller-supplied id, and the one echoed back. */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** MDC key; also the field name in the structured (ECS) log output. */
    public static final String MDC_KEY = "requestId";

    /** Bounds what a caller can write into the logs. */
    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = sanitize(request.getHeader(REQUEST_ID_HEADER));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Accepts only short, printable, non-whitespace ids. An inbound header is
     * attacker-controlled and lands in the logs, so anything that could forge a
     * log line — newlines above all — is rejected and replaced by a fresh id.
     */
    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.';
            if (!allowed) {
                return null;
            }
        }
        return trimmed;
    }
}
