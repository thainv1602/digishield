package com.digishield.ai.api;

import com.digishield.ai.api.dto.AttachmentView;
import com.digishield.ai.domain.BodyFormat;
import com.digishield.ai.domain.Difficulty;
import com.digishield.ai.domain.TemplateChannel;

import java.util.List;

/**
 * Editable content of a simulation template (Content Studio authoring). Used for
 * both create and update; on update a {@code null} field means "leave unchanged".
 *
 * @param channel     delivery channel
 * @param subject     subject / hook line
 * @param body        message body (plain text or HTML per {@code bodyFormat})
 * @param bodyFormat  how {@code body} is rendered (TEXT/HTML)
 * @param category    free-text theme (e.g. "Cơ quan thuế")
 * @param logoUrl     impersonated brand logo URL/data-URI
 * @param attachments simulated attachment metadata ({@code null} = leave unchanged)
 * @param difficulty  difficulty level
 */
public record TemplateInput(
        TemplateChannel channel,
        String subject,
        String body,
        BodyFormat bodyFormat,
        String category,
        String logoUrl,
        List<AttachmentView> attachments,
        Difficulty difficulty) {
}
