package com.digishield.ai.domain;

/**
 * Delivery channel of an AI-generated simulation template. Mirrors the
 * {@code SimTemplate.channel} enum of the OpenAPI spec.
 */
public enum TemplateChannel {
    EMAIL,
    SMS,
    QR,
    USB,
    VOICE,
    ZALO,
    TEAMS,
    SLACK;

    /**
     * Parses a wire value (lowercase, e.g. {@code "email"}) into a channel,
     * falling back to {@link #EMAIL} when the value is missing or unknown.
     */
    public static TemplateChannel fromWire(String value) {
        if (value == null || value.isBlank()) {
            return EMAIL;
        }
        try {
            return TemplateChannel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return EMAIL;
        }
    }
}
