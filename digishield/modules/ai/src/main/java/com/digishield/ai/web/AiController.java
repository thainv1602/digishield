package com.digishield.ai.web;

import com.digishield.ai.api.AiService;
import com.digishield.ai.api.dto.AidaRunView;
import com.digishield.ai.api.dto.ClassificationView;
import com.digishield.ai.api.dto.ModerationView;
import com.digishield.ai.api.dto.SimTemplateView;
import com.digishield.ai.domain.Difficulty;
import com.digishield.ai.domain.TemplateChannel;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
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
     * Authors a new simulation template (Content Studio). Persisted as a draft
     * unless {@code approved} is true.
     */
    @PreAuthorize("hasRole('CONTENT_EDITOR')")
    @PostMapping("/templates")
    public ResponseEntity<SimTemplateView> createTemplate(@RequestBody UpsertTemplateRequest request) {
        SimTemplateView view = aiService.createTemplate(
                TemplateChannel.fromWire(request.channel()),
                request.subject(),
                request.body(),
                request.category(),
                parseDifficulty(request.difficulty()),
                Boolean.TRUE.equals(request.approved()));
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    /**
     * Updates an existing template's editable fields (only provided values apply).
     */
    @PreAuthorize("hasRole('CONTENT_EDITOR')")
    @PatchMapping("/templates/{id}")
    public ResponseEntity<SimTemplateView> updateTemplate(@PathVariable("id") UUID id,
                                                          @RequestBody UpsertTemplateRequest request) {
        SimTemplateView view = aiService.updateTemplate(
                id,
                request.channel() != null ? TemplateChannel.fromWire(request.channel()) : null,
                request.subject(),
                request.body(),
                request.category(),
                parseDifficulty(request.difficulty()));
        return ResponseEntity.ok(view);
    }

    /**
     * Submits a template for use (DRAFT → APPROVED).
     */
    @PreAuthorize("hasRole('CONTENT_EDITOR')")
    @PostMapping("/templates/{id}/submit")
    public ResponseEntity<SimTemplateView> submitTemplate(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(aiService.submitTemplate(id));
    }

    /**
     * Deletes a template from the library.
     */
    @PreAuthorize("hasRole('CONTENT_EDITOR')")
    @DeleteMapping("/templates/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable("id") UUID id) {
        aiService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    /** Parses a lowercase difficulty ({@code easy|medium|hard}); {@code null}/blank → null. */
    private static Difficulty parseDifficulty(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Difficulty.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
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

    /**
     * DTO for authoring (create) or editing (patch) a template. On patch, omitted
     * fields ({@code null}) are left unchanged.
     */
    public record UpsertTemplateRequest(
            @JsonProperty("channel") String channel,
            @JsonProperty("subject") String subject,
            @JsonProperty("body") String body,
            @JsonProperty("category") String category,
            @JsonProperty("difficulty") String difficulty,
            @JsonProperty("approved") Boolean approved) {
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
