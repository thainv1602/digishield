package com.digishield.ai.application;

import com.digishield.ai.domain.Difficulty;

/**
 * Output of {@link AiClient#generate}: the generated template content the
 * service persists as an {@code AiTemplate} draft.
 *
 * @param subject    subject / hook line
 * @param bodyRef    stable slug/reference for the rendered body
 * @param body       the generated message body (the phishing email/SMS content)
 * @param difficulty difficulty level
 */
public record GeneratedTemplate(String subject, String bodyRef, String body, Difficulty difficulty) {
}
