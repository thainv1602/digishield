package com.digishield.ai.web;

import com.digishield.ai.api.AiService;
import com.digishield.ai.api.dto.ClassificationView;
import com.digishield.ai.api.dto.ModerationView;
import com.digishield.ai.api.dto.SimTemplateView;
import com.digishield.ai.domain.TemplateChannel;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for the AI module.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * Generates a simulated phishing template draft (pending approval).
     */
    @PostMapping("/templates/generate")
    public ResponseEntity<SimTemplateView> generate(@RequestBody GenerateTemplateRequest request) {
        SimTemplateView view = aiService.generateTemplate(
                TemplateChannel.fromWire(request.channel()),
                request.industry(),
                request.season());
        return ResponseEntity.ok(view);
    }

    /**
     * Classifies a reported email payload.
     */
    @PostMapping("/classify")
    public ResponseEntity<ClassificationView> classify(@RequestBody ClassifyRequest request) {
        return ResponseEntity.ok(aiService.classify(request.payload()));
    }

    /**
     * Safety-moderates AI-generated content.
     */
    @PostMapping("/moderate")
    public ResponseEntity<ModerationView> moderate(@RequestBody ModerateRequest request) {
        return ResponseEntity.ok(aiService.moderate(request.content()));
    }

    /**
     * Runs the AIDA orchestration flow (recompute risk and auto-enroll).
     */
    @PostMapping("/orchestration/run")
    public ResponseEntity<Void> runOrchestration(@RequestBody(required = false) OrchestrationRunRequest request) {
        String scope = request == null ? null : request.scope();
        UUID scopeId = request == null ? null : request.scopeId();
        aiService.runOrchestration(scope, scopeId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /** DTO for template generation request. */
    public record GenerateTemplateRequest(
            @JsonProperty("channel") String channel,
            @JsonProperty("industry") String industry,
            @JsonProperty("season") String season) {
    }

    /** DTO for classification request. */
    public record ClassifyRequest(@JsonProperty("payload") String payload) {
    }

    /** DTO for moderation request. */
    public record ModerateRequest(@JsonProperty("content") String content) {
    }

    /** DTO for orchestration run request. */
    public record OrchestrationRunRequest(
            @JsonProperty("scope") String scope,
            @JsonProperty("scope_id") UUID scopeId) {
    }
}
