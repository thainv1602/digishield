package com.digishield.learning.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Wire view of an {@code Assessment} (OpenAPI {@code Assessment} schema).
 * Serializes/deserializes with snake_case field names.
 *
 * @param id            assessment identifier (may be null on create)
 * @param type          assessment type (knowledge|culture|placement)
 * @param anonymous     whether responses are collected anonymously
 * @param questionsJson question set as a JSON object
 * @param period        free-form period label
 */
public record AssessmentView(
        @JsonProperty("id") UUID id,
        @JsonProperty("type") String type,
        @JsonProperty("anonymous") boolean anonymous,
        @JsonProperty("questions_json") Object questionsJson,
        @JsonProperty("period") String period) {
}
