package com.digishield;

import com.digishield.auth.api.AuthService;
import com.digishield.auth.api.UserView;
import com.digishield.notification.api.RecipientResolver;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Wires the notification module's {@link RecipientResolver} SPI to the auth
 * module: looks up a user's email/phone so notifications can be delivered. Living
 * in the boot app keeps the notification module decoupled from auth at the
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
        return contactOf(userId, UserView::email);
    }

    @Override
    public Optional<String> phoneFor(UUID userId) {
        return contactOf(userId, UserView::phone);
    }

    private Optional<String> contactOf(UUID userId, java.util.function.Function<UserView, String> field) {
        try {
            UserView user = authService.getUser(userId);
            String value = user == null ? null : field.apply(user);
            return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
        } catch (RuntimeException e) {
            // No such user in the tenant, or lookup failed — treat as unresolved.
            return Optional.empty();
        }
    }
}
