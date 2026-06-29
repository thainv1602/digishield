package com.digishield;

import com.digishield.auth.api.AuthService;
import com.digishield.auth.api.UserView;
import com.digishield.notification.api.UserDirectory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Wires the notification module's {@link UserDirectory} SPI to the auth module:
 * enumerates the current tenant's users so a broadcast can fan out to all of
 * them. Lives in the boot app to keep notification decoupled from auth.
 */
@Component
class AuthUserDirectory implements UserDirectory {

    private final AuthService authService;

    AuthUserDirectory(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public List<UUID> allUserIds() {
        return authService.listUsers().stream()
                .map(UserView::id)
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
