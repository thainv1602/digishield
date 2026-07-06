package com.digishield.ai.application;

import com.digishield.ai.api.dto.AidaRunView;
import com.digishield.ai.api.dto.SimTemplateView;
import com.digishield.ai.domain.AidaRun;
import com.digishield.ai.domain.AiTemplate;
import com.digishield.ai.domain.Difficulty;
import com.digishield.ai.domain.TemplateChannel;
import com.digishield.ai.domain.TemplateStatus;
import com.digishield.ai.infrastructure.AidaRunRepository;
import com.digishield.ai.infrastructure.AiTemplateRepository;
import com.digishield.contracts.events.AidaOrchestrationRequestedEvent;
import com.digishield.shared.messaging.EventPublisher;
import com.digishield.shared.tenantcontext.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiServiceImpl}.
 * <p>
 * The implementation uses deterministic, dependency-free stubs (the real LLM
 * calls are marked TODO). Pure Mockito unit tests: no Spring context, no DB.
 */
@ExtendWith(MockitoExtension.class)
class AiServiceImplTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private AiTemplateRepository templateRepository;

    @Mock
    private AidaRunRepository aidaRunRepository;

    @Mock
    private AiClient aiClient;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private com.digishield.shared.tenantcontext.Messages messages;

    @InjectMocks
    private AiServiceImpl aiService;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID.toString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void generateTemplate_persistsClientOutputAsDraftAndReturnsView() {
        // Arrange: the AI client produces the content; the service persists it
        when(aiClient.generate(TemplateChannel.EMAIL, "banking", "summer"))
                .thenReturn(new GeneratedTemplate("[banking] Cảnh báo", "tmpl/email/banking", Difficulty.MEDIUM));
        when(templateRepository.save(any(AiTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        SimTemplateView view = aiService.generateTemplate(TemplateChannel.EMAIL, "banking", "summer");

        // Assert: persisted as a lowercase-mapped DRAFT view
        assertThat(view.id()).isNotNull();
        assertThat(view.channel()).isEqualTo("email");
        assertThat(view.subject()).isEqualTo("[banking] Cảnh báo");
        assertThat(view.bodyRef()).isEqualTo("tmpl/email/banking");
        assertThat(view.difficulty()).isEqualTo("medium");
        assertThat(view.status()).isEqualTo("draft");
        verify(templateRepository).save(any(AiTemplate.class));
    }

    @Test
    void listTemplates_returnsTenantTemplatesAsLowercaseViews() {
        // Arrange
        AiTemplate t = new AiTemplate(
                UUID.randomUUID(), TENANT_ID, TemplateChannel.SMS, "Thông báo",
                "tmpl/sms/x", Difficulty.EASY, TemplateStatus.APPROVED);
        when(templateRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(t));

        // Act
        List<SimTemplateView> views = aiService.listTemplates();

        // Assert: enum-backed fields emitted as lowercase, mapped per tenant
        assertThat(views).hasSize(1);
        SimTemplateView v = views.get(0);
        assertThat(v.channel()).isEqualTo("sms");
        assertThat(v.difficulty()).isEqualTo("easy");
        assertThat(v.status()).isEqualTo("approved");
        assertThat(v.subject()).isEqualTo("Thông báo");
    }

    @Test
    void classify_delegatesToAiClient() {
        // Arrange
        when(aiClient.classify("x")).thenReturn(new com.digishield.ai.api.dto.ClassificationView("threat", 0.8, "r"));

        // Act + Assert
        assertThat(aiService.classify("x").label()).isEqualTo("threat");
    }

    @Test
    void runOrchestration_recordsRunningRunAndPublishesRequest() {
        // Arrange
        UUID scopeId = UUID.randomUUID();
        ArgumentCaptor<AidaRun> runCaptor = ArgumentCaptor.forClass(AidaRun.class);
        ArgumentCaptor<AidaOrchestrationRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(AidaOrchestrationRequestedEvent.class);
        when(messages.get(any(), any())).thenReturn("Đang tính lại rủi ro cho phạm vi \"org\"…");

        // Act
        aiService.runOrchestration("org", scopeId);

        // Assert: a "running" run is recorded for the tenant/scope...
        verify(aidaRunRepository).save(runCaptor.capture());
        AidaRun saved = runCaptor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.getScope()).isEqualTo("org");
        assertThat(saved.getScopeId()).isEqualTo(scopeId);
        assertThat(saved.getStatus()).isEqualTo("running");
        assertThat(saved.getSummary()).isNotBlank();
        assertThat(saved.getCreatedAt()).isNotNull();

        // ...and the pipeline is kicked off via an event carrying the same run id.
        verify(eventPublisher).publish(eventCaptor.capture());
        AidaOrchestrationRequestedEvent event = eventCaptor.getValue();
        assertThat(event.tenantId()).isEqualTo(TENANT_ID);
        assertThat(event.runId()).isEqualTo(saved.getId());
        assertThat(event.scope()).isEqualTo("org");
        assertThat(event.scopeId()).isEqualTo(scopeId);

        // ...and the template library is not touched.
        verifyNoInteractions(templateRepository);
    }

    @Test
    void listRuns_returnsTenantRunsAsViews() {
        // Arrange
        AidaRun run = new AidaRun(
                UUID.randomUUID(), TENANT_ID, "Phòng Kế toán", null, "success",
                "34 người được cập nhật lộ trình học.", java.time.Instant.now());
        when(aidaRunRepository.findByTenantIdOrderByCreatedAtDesc(TENANT_ID)).thenReturn(List.of(run));

        // Act
        List<AidaRunView> views = aiService.listRuns();

        // Assert
        assertThat(views).hasSize(1);
        AidaRunView v = views.get(0);
        assertThat(v.scope()).isEqualTo("Phòng Kế toán");
        assertThat(v.status()).isEqualTo("success");
        assertThat(v.summary()).contains("34 người");
    }
}
