package com.digishield.ai.web;

import com.digishield.ai.api.AiService;
import com.digishield.ai.api.dto.AidaRunView;
import com.digishield.ai.api.dto.ClassificationView;
import com.digishield.ai.api.dto.ModerationView;
import com.digishield.ai.api.dto.SimTemplateView;
import com.digishield.ai.domain.TemplateChannel;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

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
    @PreAuthorize("hasRole('CONTENT_EDITOR')")
    @PostMapping("/templates/generate")
    public ResponseEntity<SimTemplateView> generate(@RequestBody GenerateTemplateRequest request) {
        SimTemplateView view = aiService.generateTemplate(
                TemplateChannel.fromWire(request.channel()),
                request.industry(),
                request.season());
        return ResponseEntity.ok(view);
    }

    /**
     * Lists the saved simulation-template library for the current tenant.
     */
    @PreAuthorize("hasRole('CONTENT_EDITOR')")
    @GetMapping("/templates")
    public ResponseEntity<List<SimTemplateView>> listTemplates() {
        return ResponseEntity.ok(aiService.listTemplates());
    }

    /**
     * Classifies a reported email payload.
     */
    @PreAuthorize("hasRole('ANALYST')")
    @PostMapping("/classify")
    public ResponseEntity<ClassificationView> classify(@RequestBody ClassifyRequest request) {
        return ResponseEntity.ok(aiService.classify(request.payload()));
    }

    /**
     * Safety-moderates AI-generated content.
     */
    @PreAuthorize("hasRole('ANALYST')")
    @PostMapping("/moderate")
    public ResponseEntity<ModerationView> moderate(@RequestBody ModerateRequest request) {
        return ResponseEntity.ok(aiService.moderate(request.content()));
    }

    /**
     * Runs the AIDA orchestration flow (recompute risk and auto-enroll).
     */
    @PreAuthorize("hasRole('ORG_ADMIN')")
    @PostMapping("/orchestration/run")
    public ResponseEntity<Void> runOrchestration(@RequestBody(required = false) OrchestrationRunRequest request) {
        String scope = request == null ? null : request.scope();
        UUID scopeId = request == null ? null : request.scopeId();
        aiService.runOrchestration(scope, scopeId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * Lists past AIDA orchestration runs for the recent-runs panel.
     */
    @PreAuthorize("hasRole('ORG_ADMIN')")
    @GetMapping("/orchestration/runs")
    public ResponseEntity<List<AidaRunView>> listRuns() {
        return ResponseEntity.ok(aiService.listRuns());
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
