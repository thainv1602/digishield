package com.digishield;

import com.digishield.auth.api.AuthService;
import com.digishield.auth.api.UserView;
import com.digishield.notification.api.RecipientResolver;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Wires the notification module's {@link RecipientResolver} SPI to the auth
 * module: looks up a user's email so notifications can be delivered. Living in
 * the boot app keeps the notification module decoupled from auth at the
 * module-boundary level.
 */
@Component
class AuthRecipientResolver implements RecipientResolver {

    private final AuthService authService;

    AuthRecipientResolver(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public Optional<String> emailFor(UUID userId) {
        try {
            UserView user = authService.getUser(userId);
            String email = user == null ? null : user.email();
            return (email == null || email.isBlank()) ? Optional.empty() : Optional.of(email);
        } catch (RuntimeException e) {
            // No such user in the tenant, or lookup failed — treat as unresolved.
            return Optional.empty();
        }
    }
}
