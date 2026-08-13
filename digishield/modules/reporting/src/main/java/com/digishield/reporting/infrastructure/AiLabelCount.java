package com.digishield.reporting.infrastructure;

import com.digishield.reporting.domain.AiLabel;

/**
 * One row of the open-report tally: how many still-open reports carry a given
 * AI label.
 *
 * @param aiLabel the label, or null for reports the classifier never labelled
 * @param count   how many open reports carry it
 */
public record AiLabelCount(AiLabel aiLabel, long count) {
}
