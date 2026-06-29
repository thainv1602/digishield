package com.digishield.notification.api;

import java.util.Optional;
import java.util.UUID;

/**
 * SPI for resolving a user's contact address so a notification can be delivered.
 * <p>
 * The notification module does not own user contact data; the boot application
 * supplies the implementation (backed by the auth module). This keeps the
 * notification module decoupled from auth at the module-boundary level.
 */
public interface RecipientResolver {

    /**
     * Returns the email address for a user in the current tenant, or empty if it
     * cannot be resolved.
     */
    Optional<String> emailFor(UUID userId);
}
