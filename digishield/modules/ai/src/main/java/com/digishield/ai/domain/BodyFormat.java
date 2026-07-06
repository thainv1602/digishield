package com.digishield.ai.domain;

/**
 * How a template's {@code body} should be interpreted/rendered.
 * <ul>
 *   <li>{@link #TEXT} — plain text (SMS/Zalo and simple emails).</li>
 *   <li>{@link #HTML} — rich HTML (branded email with an embedded logo/images).</li>
 * </ul>
 */
public enum BodyFormat {
    TEXT,
    HTML
}
