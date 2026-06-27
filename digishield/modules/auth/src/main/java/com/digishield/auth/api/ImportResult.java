package com.digishield.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of a bulk user import, matching the {@code /users/import} OpenAPI schema.
 *
 * <p>JSON shape: {@code { "job_id": "...", "accepted": 42 }}. In the dev profile the
 * import is processed synchronously and {@code accepted} simply reflects the number
 * of input rows that were created.
 *
 * @param jobId    identifier of the (async) import job
 * @param accepted number of users accepted for processing
 */
public record ImportResult(
        @JsonProperty("job_id") String jobId,
        int accepted) {
}
