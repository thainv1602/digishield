package com.digishield.notification.api;

import java.util.List;
import java.util.UUID;

/**
 * SPI for enumerating the users a broadcast should reach. The notification
 * module does not own the user list; the boot application supplies the
 * implementation (backed by the auth module), keeping the modules decoupled.
 */
public interface UserDirectory {

    /** Ids of all users in the current tenant (broadcast audience). */
    List<UUID> allUserIds();
}
