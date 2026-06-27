package com.digishield.ai.domain;

/**
 * Approval status of an AI-generated simulation template. Mirrors the
 * {@code SimTemplate.status} enum of the OpenAPI spec. AI drafts always start
 * as {@link #DRAFT} (pending human approval).
 */
public enum TemplateStatus {
    DRAFT,
    APPROVED
}
