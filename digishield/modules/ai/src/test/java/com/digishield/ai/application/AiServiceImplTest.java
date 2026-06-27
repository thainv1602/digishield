package com.digishield.ai.application;

import com.digishield.ai.api.dto.ClassificationView;
import com.digishield.ai.api.dto.ModerationView;
import com.digishield.ai.api.dto.SimTemplateView;
import com.digishield.ai.domain.AiTemplate;
import com.digishield.ai.domain.TemplateChannel;
import com.digishield.ai.infrastructure.AiTemplateRepository;
import com.digishield.shared.tenantcontext.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void generateTemplate_persistsDraftAndReturnsSimTemplateShape() {
        // Arrange
        when(templateRepository.save(any(AiTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        SimTemplateView view = aiService.generateTemplate(TemplateChannel.EMAIL, "banking", "summer");

        // Assert
        assertThat(view).isNotNull();
        assertThat(view.id()).isNotNull();
        assertThat(view.channel()).isEqualTo("email");
        assertThat(view.subject()).contains("banking");
        assertThat(view.bodyRef()).isNotBlank();
        assertThat(view.difficulty()).isIn("easy", "medium", "hard");
        assertThat(view.status()).isEqualTo("draft");
        verify(templateRepository).save(any(AiTemplate.class));
    }

    @Test
    void classify_flagsThreatSignals() {
        // Act
        ClassificationView result = aiService.classify("Please verify your account and enter your password");

        // Assert
        assertThat(result.label()).isEqualTo("threat");
        assertThat(result.confidence()).isBetween(0.0, 1.0);
        assertThat(result.reason()).isNotBlank();
    }

    @Test
    void classify_returnsCleanForBenignContent() {
        // Act
        ClassificationView result = aiService.classify("Hello, see you at the team lunch tomorrow.");

        // Assert
        assertThat(result.label()).isEqualTo("clean");
    }

    @Test
    void moderate_passesSafeContent() {
        // Act
        ModerationView result = aiService.moderate("hello world");

        // Assert
        assertThat(result.verdict()).isEqualTo("pass");
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void moderate_blocksContentWithMultipleUnsafeWords() {
        // Act
        ModerationView result = aiService.moderate("this ransomware can exploit your system");

        // Assert
        assertThat(result.verdict()).isEqualTo("block");
        assertThat(result.reasons()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void runOrchestration_isANoOpThatDoesNotTouchRepository() {
        // Act
        aiService.runOrchestration("org", UUID.randomUUID());

        // Assert
        verifyNoInteractions(templateRepository);
    }
}
