package com.digishield.ai.web;

import com.digishield.ai.api.AiService;
import com.digishield.ai.domain.TemplateDraft;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sample REST controller for the AI module.
 */
@RestController
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * Generates a template from a prompt (sample).
     */
    @PostMapping("/api/v1/ai/templates/generate")
    public TemplateDraft generate(@RequestBody GenerateTemplateRequest request) {
        return aiService.generateTemplate(request.prompt());
    }

    /**
     * Classifies report content (sample).
     */
    @PostMapping("/api/v1/ai/classify")
    public ClassifyResponse classify(@RequestBody ClassifyRequest request) {
        return new ClassifyResponse(aiService.classifyReport(request.content()));
    }

    /** DTO for template generation request. */
    public record GenerateTemplateRequest(String prompt) {
    }

    /** DTO for classification request. */
    public record ClassifyRequest(String content) {
    }

    /** DTO for classification response. */
    public record ClassifyResponse(String label) {
    }
}
