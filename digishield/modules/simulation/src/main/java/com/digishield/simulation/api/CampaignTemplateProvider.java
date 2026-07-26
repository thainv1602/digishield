package com.digishield.simulation.api;

import java.util.Optional;
import java.util.UUID;

/**
 * SPI that resolves the message content of the template a campaign was created
 * with, so a launched campaign delivers the authored lure instead of a generic
 * placeholder.
 *
 * <p>Templates live in the AI module (Content Studio authors them there), but the
 * simulation module must not depend on it. The application shell supplies the
 * implementation — the same pattern as the notification module's
 * {@code RecipientResolver}. When no bean is present (or the id is unknown), the
 * simulation module simply sends without template content and the notification
 * module falls back to its generic message.
 */
public interface CampaignTemplateProvider {

    /**
     * Message content of a simulation template.
     *
     * @param subject    subject / hook line, used as the message title
     * @param body       message body; may contain the {@code {{link}}} placeholder,
     *                   which the delivery path replaces with the recipient's
     *                   tracking URL (appended when the placeholder is absent)
     * @param bodyFormat {@code "text"} or {@code "html"}
     */
    record TemplateContent(String subject, String body, String bodyFormat) {
    }

    /**
     * Resolves a template in the current tenant. Returns empty when the id is
     * {@code null}, unknown, or owned by another tenant — never throws, because a
     * missing template must not fail a campaign launch.
     */
    Optional<TemplateContent> findById(UUID templateId);
}
