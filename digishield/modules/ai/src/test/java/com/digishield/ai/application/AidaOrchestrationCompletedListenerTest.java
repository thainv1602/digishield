package com.digishield.ai.application;

import com.digishield.ai.domain.AidaRun;
import com.digishield.ai.infrastructure.AidaRunRepository;
import com.digishield.contracts.events.AidaOrchestrationCompletedEvent;
import com.digishield.shared.tenantcontext.Messages;
import com.digishield.shared.tenantcontext.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AidaOrchestrationCompletedListener}. Uses a real
 * {@link Messages} over the {@code messages.properties} bundle (Vietnamese) so the
 * assertions exercise the actual localized summaries.
 */
class AidaOrchestrationCompletedListenerTest {

    private final AidaRunRepository aidaRunRepository = mock(AidaRunRepository.class);
    private final AidaOrchestrationCompletedListener listener =
            new AidaOrchestrationCompletedListener(aidaRunRepository, viMessages());

    private static Messages viMessages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return new Messages(source);
    }

    @BeforeEach
    void setLocale() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("vi"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void on_finalisesRunWithSuccessAndRealCounts() {
        // Arrange: a run left in "running" by the trigger.
        UUID tenantId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AidaRun run = new AidaRun(runId, tenantId, "org", null, "running", "…", Instant.now());
        when(aidaRunRepository.findById(runId)).thenReturn(Optional.of(run));
        ArgumentCaptor<AidaRun> captor = ArgumentCaptor.forClass(AidaRun.class);

        // Act
        listener.on(new AidaOrchestrationCompletedEvent(tenantId, runId, 5, 2, "vi"));

        // Assert: flipped to success with a summary carrying the counts, context cleared.
        verify(aidaRunRepository).save(captor.capture());
        AidaRun saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("success");
        assertThat(saved.getSummary()).contains("5").contains("2");
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void on_englishLocale_writesEnglishSummary() {
        // Arrange: the run was triggered by an English request, carried on the event.
        UUID tenantId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AidaRun run = new AidaRun(runId, tenantId, "org", null, "running", "…", Instant.now());
        when(aidaRunRepository.findById(runId)).thenReturn(Optional.of(run));

        // Act
        listener.on(new AidaOrchestrationCompletedEvent(tenantId, runId, 5, 2, "en"));

        // Assert: the summary resolves in English (matching the trigger), not the default VI,
        // and the listener does not leak the locale onto its pooled thread.
        assertThat(run.getStatus()).isEqualTo("success");
        assertThat(run.getSummary()).contains("Recomputed risk").contains("5").contains("2");
        // The event's "en" locale must not leak onto this pooled listener thread.
        assertThat(LocaleContextHolder.getLocale()).isNotEqualTo(Locale.forLanguageTag("en"));
    }

    @Test
    void on_zeroUsers_writesNoUsersSummary() {
        UUID tenantId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AidaRun run = new AidaRun(runId, tenantId, "org", null, "running", "…", Instant.now());
        when(aidaRunRepository.findById(runId)).thenReturn(Optional.of(run));

        listener.on(new AidaOrchestrationCompletedEvent(tenantId, runId, 0, 0, "vi"));

        assertThat(run.getStatus()).isEqualTo("success");
        assertThat(run.getSummary()).contains("Không có người dùng");
    }

    @Test
    void on_unknownRun_doesNothing() {
        UUID runId = UUID.randomUUID();
        when(aidaRunRepository.findById(runId)).thenReturn(Optional.empty());

        listener.on(new AidaOrchestrationCompletedEvent(UUID.randomUUID(), runId, 3, 1, "en"));

        verify(aidaRunRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
