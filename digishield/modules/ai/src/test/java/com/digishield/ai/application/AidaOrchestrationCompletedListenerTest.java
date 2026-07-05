package com.digishield.ai.application;

import com.digishield.ai.domain.AidaRun;
import com.digishield.ai.infrastructure.AidaRunRepository;
import com.digishield.contracts.events.AidaOrchestrationCompletedEvent;
import com.digishield.shared.tenantcontext.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AidaOrchestrationCompletedListener}.
 */
class AidaOrchestrationCompletedListenerTest {

    private final AidaRunRepository aidaRunRepository = mock(AidaRunRepository.class);
    private final AidaOrchestrationCompletedListener listener =
            new AidaOrchestrationCompletedListener(aidaRunRepository);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
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
        listener.on(new AidaOrchestrationCompletedEvent(tenantId, runId, 5, 2));

        // Assert: flipped to success with a summary carrying the counts, context cleared.
        verify(aidaRunRepository).save(captor.capture());
        AidaRun saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("success");
        assertThat(saved.getSummary()).contains("5").contains("2");
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void on_zeroUsers_writesNoUsersSummary() {
        UUID tenantId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AidaRun run = new AidaRun(runId, tenantId, "org", null, "running", "…", Instant.now());
        when(aidaRunRepository.findById(runId)).thenReturn(Optional.of(run));

        listener.on(new AidaOrchestrationCompletedEvent(tenantId, runId, 0, 0));

        assertThat(run.getStatus()).isEqualTo("success");
        assertThat(run.getSummary()).contains("Không có người dùng");
    }

    @Test
    void on_unknownRun_doesNothing() {
        UUID runId = UUID.randomUUID();
        when(aidaRunRepository.findById(runId)).thenReturn(Optional.empty());

        listener.on(new AidaOrchestrationCompletedEvent(UUID.randomUUID(), runId, 3, 1));

        verify(aidaRunRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
