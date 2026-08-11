package com.digishield.ai.application;

import com.digishield.ai.api.TemplateInput;
import com.digishield.ai.api.dto.AttachmentView;
import com.digishield.ai.domain.AiTemplate;
import com.digishield.ai.domain.BodyFormat;
import com.digishield.ai.domain.Difficulty;
import com.digishield.ai.domain.TemplateChannel;
import com.digishield.ai.domain.TemplateStatus;
import com.digishield.ai.infrastructure.AiTemplateRepository;
import com.digishield.shared.tenantcontext.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Editing a simulation template.
 *
 * <p>A partial update must leave the fields it does not mention alone: these
 * templates reach real employees, and a save that silently blanks a body or an
 * attachment list produces a campaign nobody reviewed in that state.
 */
@ExtendWith(MockitoExtension.class)
class AiTemplateUpdateTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private AiTemplateRepository templateRepository;

    @Mock
    private com.digishield.ai.infrastructure.AidaRunRepository aidaRunRepository;

    @Mock
    private AiClient aiClient;

    @Mock
    private com.digishield.shared.messaging.EventPublisher eventPublisher;

    @Mock
    private com.digishield.shared.tenantcontext.Messages messages;

    private AiServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT.toString());
        service = new AiServiceImpl(templateRepository, aidaRunRepository, aiClient,
                eventPublisher, messages, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private AiTemplate existing(UUID id, UUID tenantId) {
        return new AiTemplate(id, tenantId, TemplateChannel.EMAIL, "Cảnh báo bảo mật",
                "ref-1", "Kính gửi Quý khách", "ngân hàng", Difficulty.MEDIUM,
                TemplateStatus.DRAFT);
    }

    @Test
    @DisplayName("an update touches only the fields it carries")
    void omittedFieldsSurvive() {
        UUID id = UUID.randomUUID();
        AiTemplate template = existing(id, TENANT);
        when(templateRepository.findById(id)).thenReturn(Optional.of(template));
        when(templateRepository.save(any(AiTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateTemplate(id, new TemplateInput(
                null, "Chủ đề mới", null, null, null, null, null, null));

        assertThat(template.getSubject()).isEqualTo("Chủ đề mới");
        assertThat(template.getBody()).isEqualTo("Kính gửi Quý khách");
        assertThat(template.getChannel()).isEqualTo(TemplateChannel.EMAIL);
        assertThat(template.getDifficulty()).isEqualTo(Difficulty.MEDIUM);
    }

    @Test
    @DisplayName("a blank subject is ignored rather than blanking the template")
    void aBlankSubjectIsNotAnUpdate() {
        UUID id = UUID.randomUUID();
        AiTemplate template = existing(id, TENANT);
        when(templateRepository.findById(id)).thenReturn(Optional.of(template));
        when(templateRepository.save(any(AiTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateTemplate(id, new TemplateInput(
                null, "   ", null, null, null, null, null, null));

        assertThat(template.getSubject()).isEqualTo("Cảnh báo bảo mật");
    }

    @Test
    @DisplayName("channel, format and difficulty are replaced when given")
    void statedFieldsAreApplied() {
        UUID id = UUID.randomUUID();
        AiTemplate template = existing(id, TENANT);
        when(templateRepository.findById(id)).thenReturn(Optional.of(template));
        when(templateRepository.save(any(AiTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateTemplate(id, new TemplateInput(
                TemplateChannel.SMS, null, "Ngắn gọn", BodyFormat.TEXT,
                "bảo hiểm", null, null, Difficulty.HARD));

        assertThat(template.getChannel()).isEqualTo(TemplateChannel.SMS);
        assertThat(template.getBody()).isEqualTo("Ngắn gọn");
        assertThat(template.getBodyFormat()).isEqualTo(BodyFormat.TEXT);
        assertThat(template.getCategory()).isEqualTo("bảo hiểm");
        assertThat(template.getDifficulty()).isEqualTo(Difficulty.HARD);
    }

    @Test
    @DisplayName("attachments survive a round trip through storage")
    void attachmentsAreStoredAndRead() {
        UUID id = UUID.randomUUID();
        AiTemplate template = existing(id, TENANT);
        when(templateRepository.findById(id)).thenReturn(Optional.of(template));
        when(templateRepository.save(any(AiTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        var view = service.updateTemplate(id, new TemplateInput(
                null, null, null, null, null, null,
                List.of(new AttachmentView("hoa-don.pdf", "application/pdf")), null));

        assertThat(view.attachments())
                .extracting(AttachmentView::name)
                .containsExactly("hoa-don.pdf");
    }

    @Test
    @DisplayName("a template of another tenant cannot be edited")
    void anotherTenantsTemplateIsNotFound() {
        UUID id = UUID.randomUUID();
        when(templateRepository.findById(id)).thenReturn(Optional.of(existing(id, OTHER_TENANT)));

        assertThatThrownBy(() -> service.updateTemplate(id, new TemplateInput(
                null, "Đổi", null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(templateRepository, never()).save(any());
    }
}
