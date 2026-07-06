package com.digishield.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A simulated attachment on a template (metadata only — no real file is stored).
 * Used to render fake attachment chips (e.g. a bogus "Thong_bao.pdf") in the
 * Content Studio preview and the delivered simulation.
 *
 * @param name display file name (e.g. {@code "Thong_bao_hoan_thue.pdf"})
 * @param mime MIME type (e.g. {@code "application/pdf"}); may be {@code null}
 */
public record AttachmentView(
        @JsonProperty("name") String name,
        @JsonProperty("mime") String mime) {
}
