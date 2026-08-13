package com.digishield.reporting.api.dto;

/**
 * How many reports are still awaiting triage, split by the verdict the AI gave
 * them.
 *
 * <p>One field per {@code AiLabel} rather than a map keyed by the enum: the
 * enum lives in the module's {@code domain} package, which is not exposed
 * across the module boundary, and this record keeps the crossing to the
 * {@code api.dto} named interface. It also means a new label cannot be added
 * without a compile error at the place that decides what severity it maps to.
 *
 * @param threat open reports the AI called a threat
 * @param spam   open reports the AI called spam
 * @param clean  open reports the AI cleared; still untriaged, so still counted
 *               here, but no severity claims them
 */
public record OpenReportCountsDto(long threat, long spam, long clean) {
}
