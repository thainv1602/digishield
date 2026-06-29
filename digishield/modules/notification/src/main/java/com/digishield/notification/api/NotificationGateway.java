package com.digishield.notification.api;

/**
 * SPI for delivering a notification over an external channel (email/SMS/push).
 * <p>
 * The boot application supplies the concrete implementation (e.g. AWS SES for
 * email). When no real gateway is configured, a logging default stands in so the
 * dev profile keeps working without sending anything. Implementations should
 * throw on a delivery failure so the caller can mark the notification FAILED.
 */
public interface NotificationGateway {

    /**
     * Delivers a message to {@code recipient} over {@code channel}.
     *
     * @param channel   delivery channel name (e.g. {@code "EMAIL"}); the gateway
     *                  may skip channels it does not handle
     * @param recipient the channel address (e.g. an email), may be {@code null}
     *                  when it could not be resolved
     * @param title     message subject/title
     * @param body      message body
     * @throws RuntimeException if delivery fails
     */
    void deliver(String channel, String recipient, String title, String body);
}
