package com.digishield.ai.application;

import com.digishield.ai.domain.TemplateDraft;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AiServiceImpl}.
 * <p>
 * The implementation returns deterministic stubbed samples (the real LLM calls are
 * marked TODO). The tests assert the returned shape, non-null values and the
 * deterministic content that the stub embeds.
 */
class AiServiceImplTest {

    private final AiServiceImpl aiService = new AiServiceImpl();

    @Test
    void generateTemplate_returnsDraftEchoingPromptWithReviewVerdict() {
        // Arrange
        String prompt = "account security warning";

        // Act
        TemplateDraft draft = aiService.generateTemplate(prompt);

        // Assert
        assertThat(draft).isNotNull();
        assertThat(draft.subject()).isNotBlank();
        assertThat(draft.body()).contains(prompt);
        assertThat(draft.verdict()).isEqualTo("NEEDS_REVIEW");
    }

    @Test
    void classifyReport_returnsDeterministicLabel() {
        // Act
        String label = aiService.classifyReport("please verify your password now");

        // Assert
        assertThat(label).isEqualTo("LIKELY_PHISHING");
    }

    @Test
    void moderate_returnsSafeVerdict() {
        // Act
        String verdict = aiService.moderate("hello world");

        // Assert
        assertThat(verdict).isEqualTo("SAFE");
    }

    @Test
    void runOrchestration_returnsCompletionMarker() {
        // Act
        String result = aiService.runOrchestration("input");

        // Assert
        assertThat(result).isEqualTo("ORCHESTRATION_COMPLETED");
    }
}
