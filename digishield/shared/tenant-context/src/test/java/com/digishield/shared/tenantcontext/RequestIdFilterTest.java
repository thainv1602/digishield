package com.digishield.shared.tenantcontext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RequestIdFilter}.
 * <p>
 * The interesting cases are the hostile and the housekeeping ones: an inbound
 * header is attacker-controlled and ends up in the logs, and a leaked MDC entry
 * would mislabel the next request to reuse the pooled thread.
 */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /** Runs the filter and captures what the MDC held mid-chain. */
    private String runWithHeader(String header) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).thenReturn(header);

        AtomicReference<String> seen = new AtomicReference<>();
        doAnswer(inv -> {
            seen.set(MDC.get(RequestIdFilter.MDC_KEY));
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);
        return seen.get();
    }

    @Test
    void mintsAnIdWhenTheCallerSendsNone() throws Exception {
        String id = runWithHeader(null);

        assertThat(id).isNotNull();
        assertThat(UUID.fromString(id)).isNotNull();   // a real UUID, not junk
    }

    @Test
    void reusesACallerSuppliedId() throws Exception {
        assertThat(runWithHeader("edge-proxy-42")).isEqualTo("edge-proxy-42");
    }

    @Test
    void rejectsAnIdCarryingNewlines() throws Exception {
        // A forged newline would let a caller write their own log lines.
        String id = runWithHeader("ok\r\nINFO  forged log line");

        assertThat(id).doesNotContain("\n").doesNotContain("\r");
        assertThat(UUID.fromString(id)).isNotNull();   // replaced by a fresh one
    }

    @Test
    void rejectsAnOverlongId() throws Exception {
        String id = runWithHeader("x".repeat(200));

        assertThat(id).hasSize(36);   // canonical UUID
    }

    @Test
    void rejectsABlankId() throws Exception {
        assertThat(UUID.fromString(runWithHeader("   "))).isNotNull();
    }

    @Test
    void echoesTheIdBackToTheCaller() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).thenReturn("abc-123");

        filter.doFilter(request, response, chain);

        verify(response).setHeader(eq(RequestIdFilter.REQUEST_ID_HEADER), eq("abc-123"));
    }

    @Test
    void clearsTheMdcAfterwardsSoAPooledThreadIsNotMislabelled() throws Exception {
        runWithHeader("abc-123");

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void clearsTheMdcEvenWhenTheChainThrows() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(anyString())).thenReturn("abc-123");
        try {
            doAnswer(inv -> {
                throw new IllegalStateException("downstream blew up");
            }).when(chain).doFilter(request, response);
            filter.doFilter(request, response, chain);
        } catch (Exception expected) {
            // fall through
        }
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
