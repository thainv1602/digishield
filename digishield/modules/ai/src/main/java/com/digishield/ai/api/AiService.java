package com.digishield.ai.api;

import com.digishield.ai.api.dto.ClassificationView;
import com.digishield.ai.api.dto.ModerationView;
import com.digishield.ai.api.dto.SimTemplateView;
import com.digishield.ai.domain.TemplateChannel;

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
     * given scope. In dev this acknowledges and logs the intent (no-op).
     */
    void runOrchestration(String scope, UUID scopeId);
}
