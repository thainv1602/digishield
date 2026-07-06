package com.digishield.ai.api;

import com.digishield.ai.api.dto.AidaRunView;
import com.digishield.ai.api.dto.ClassificationView;
import com.digishield.ai.api.dto.ModerationView;
import com.digishield.ai.api.dto.SimTemplateView;
import com.digishield.ai.domain.Difficulty;
import com.digishield.ai.domain.TemplateChannel;

import java.util.List;
import java.util.UUID;

/**
 * Public API of the AI module.
 * <p>
 * The current implementation uses deterministic, dependency-free stubs (no LLM
 * SDK). Every method marks the real model call as TODO.
 */
public interface AiService {

    /**
     * Generates a simulated phishing template draft (pending approval) for the
     * given channel / industry / season and persists it for the current tenant.
     */
    SimTemplateView generateTemplate(TemplateChannel channel, String industry, String season);

    /**
     * Lists the saved simulation-template library for the current tenant
     * (most-recently the seeded/generated drafts).
     */
    List<SimTemplateView> listTemplates();

    /**
     * Authors a new simulation template for the current tenant (Content Studio).
     * Persisted with the given status ({@code DRAFT} by default when {@code null}).
     *
     * @param channel    delivery channel
     * @param subject    subject / hook line
     * @param body       the message body (phishing email/SMS content)
     * @param category   free-text theme (e.g. "Cơ quan thuế"); may be {@code null}
     * @param difficulty difficulty ({@code MEDIUM} when {@code null})
     * @param approved   when {@code true} the template is saved as APPROVED, else DRAFT
     */
    SimTemplateView createTemplate(TemplateChannel channel, String subject, String body,
                                   String category, Difficulty difficulty, boolean approved);

    /**
     * Updates an existing template's editable fields (only non-{@code null} values
     * are applied). Scoped to the current tenant.
     */
    SimTemplateView updateTemplate(UUID id, TemplateChannel channel, String subject, String body,
                                   String category, Difficulty difficulty);

    /**
     * Submits a template for use — moves it from DRAFT to APPROVED. Scoped to the
     * current tenant.
     */
    SimTemplateView submitTemplate(UUID id);

    /**
     * Deletes a template from the current tenant's library.
     */
    void deleteTemplate(UUID id);

    /**
     * Classifies a reported email payload and returns a label, confidence and
     * reasoning.
     */
    ClassificationView classify(String payload);

    /**
     * Moderates AI-generated content and returns a verdict with reasons.
     */
    ModerationView moderate(String content);

    /**
     * Runs the AIDA orchestration flow (recompute risk and auto-enroll) for the
     * given scope and records the run for the admin console. The real pipeline is
     * still TODO; the run record is created so history is available.
     */
    void runOrchestration(String scope, UUID scopeId);

    /**
     * Lists past AIDA orchestration runs for the current tenant, most recent
     * first.
     */
    List<AidaRunView> listRuns();
}
