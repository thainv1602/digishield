package com.digishield.ai.api;

import com.digishield.ai.domain.TemplateDraft;

/**
 * Public API of the AI module.
 */
public interface AiService {

    /**
     * Generates a template draft (e.g. a simulated phishing email or training content).
     */
    TemplateDraft generateTemplate(String prompt);

    /**
     * Classifies a report (e.g. determines the phishing level) and returns a label.
     */
    String classifyReport(String content);

    /**
     * Moderates content and returns a verdict.
     */
    String moderate(String content);

    /**
     * Runs a multi-step AI orchestration flow and returns a summary result.
     */
    String runOrchestration(String input);
}
