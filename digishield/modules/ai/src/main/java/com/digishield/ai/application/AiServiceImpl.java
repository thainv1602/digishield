package com.digishield.ai.application;

import com.digishield.ai.api.AiService;
import com.digishield.ai.domain.TemplateDraft;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link AiService}. The current body returns hardcoded samples for the skeleton;
 * the real LLM call is marked TODO.
 */
@Service
public class AiServiceImpl implements AiService {

    @Override
    public TemplateDraft generateTemplate(String prompt) {
        // TODO: call the LLM (AI provider) to generate content from the prompt.
        return new TemplateDraft(
                "[Mẫu] Cảnh báo bảo mật tài khoản",
                "Xin chào, đây là nội dung mô phỏng được sinh cho prompt: " + prompt,
                "NEEDS_REVIEW");
    }

    @Override
    public String classifyReport(String content) {
        // TODO: call the LLM/classification model to determine the report label.
        return "LIKELY_PHISHING";
    }

    @Override
    public String moderate(String content) {
        // TODO: call the content moderation service.
        return "SAFE";
    }

    @Override
    public String runOrchestration(String input) {
        // TODO: orchestrate multiple AI steps (generate -> moderate -> classify).
        return "ORCHESTRATION_COMPLETED";
    }
}
