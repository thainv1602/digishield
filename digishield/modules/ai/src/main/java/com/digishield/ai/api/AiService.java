package com.digishield.ai.api;

import com.digishield.ai.api.dto.AidaRunView;
import com.digishield.ai.api.dto.ClassificationView;
import com.digishield.ai.api.dto.ModerationView;
import com.digishield.ai.api.dto.SimTemplateView;
import com.digishield.ai.domain.TemplateChannel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the AI module.
 * <p>
 * Calls are served by whichever client is enabled: the self-hosted classifier
 * ({@code digishield.ai.ml.enabled}), Claude ({@code digishield.ai.claude.enabled}),
 * or the deterministic stub when neither is on. The stub is also the fallback
 * for any error or timeout, so these methods never fail because of a model.
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
     * Saved as APPROVED when {@code approved} is true, else DRAFT.
     */
    SimTemplateView createTemplate(TemplateInput input, boolean approved);

    /**
     * Updates an existing template's editable fields (only non-{@code null} values
     * on {@code input} are applied). Scoped to the current tenant.
     */
    SimTemplateView updateTemplate(UUID id, TemplateInput input);

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
     * Looks up a single template in the current tenant's library. Returns empty
     * rather than throwing when the id is unknown or belongs to another tenant,
     * so callers on the delivery path can fall back instead of failing a send.
     */
    Optional<SimTemplateView> findTemplate(UUID id);

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
     * Runs the AIDA orchestration flow for the given scope and records the run
     * for the admin console. The call returns as soon as the run is recorded:
     * the work itself is asynchronous, with analytics recomputing risk and
     * learning auto-enrolling before a completion event finalises the run.
     */
    void runOrchestration(String scope, UUID scopeId);

    /**
     * Lists past AIDA orchestration runs for the current tenant, most recent
     * first.
     */
    List<AidaRunView> listRuns();
}
